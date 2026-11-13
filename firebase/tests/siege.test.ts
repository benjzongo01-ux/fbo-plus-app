import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { adminDb, codeErreur, compte, fermerApplis, preparerAdmin } from "./outils.js";

/* Correspondant au siège : dépôt des lots, lecture des reçus scannés, validation. Guinée (données à part). */
const GN = "Guinée";

beforeAll(preparerAdmin);
afterAll(fermerApplis);

async function lot(idForever: string, montant: number) {
  const ref = adminDb().collection("lotsValidation").doc();
  await ref.set({
    statut: "a_valider", pays: GN, fboId: `fbo-${randomUUID()}`, idForever, nom: "Mamadou Diallo", telephone: "+224620000000",
    lignes: [], commandes: [], montant, ccTotal: 0.2, createdAt: new Date(),
  });
  return ref.id;
}

const RECU = (id: string, total: string) => `FOREVER LIVING PRODUCTS GUINEE
Facture de vente N° FLP-20261112-0042
Distributeur : ${id}
Mamadou DIALLO
015 Aloe Vera Gel   2   11 000
Sous-total          22 000
TVA 18%             3 960
Grand Total         ${total}`;

/** Facture de vente du Burkina, telle que la caméra la lit (ligne par ligne). */
const FACTURE_BF = (id: string) => `FOREVER LIVING PRODUCTS
BURKINA FASO
INFORMATIONS FBO
Num FBO ${id}
Nom AWA TRAORE
FACTURE DE VENTE 2026-01-05-100001
ARTICLE LIBELLE QUANTITE PRIX UNIT PRIX TOTAL
621 FOREVER IVISION 2 23 040 46 080
376 FOREVER ARTIC SEA 2 19 752 39 504
Sous Total : 85 584
TVA 18% : 15 405
Total 1 : 100 989
AIRSI : 2 020
Total 2 : 103 009
Point caisses : ,520
Taxe de timbre : 200
Grand Total : 103 209`;

/** Facture de Côte d'Ivoire : libellés et valeurs dans des colonnes séparées. */
const FACTURE_CI = (id: string) => `FOREVER LIVING PRODUCTS
COTE D'IVOIRE
Facture de vente N° 0101010A26000000001
Commande : 2026000001
${id}
AWA TRAORE
188 B12 PLUS 1,00 E 10 200 10 200
376 FOREVER ARTIC SEA 1,00 E 19 752 19 752
Total HT Brut
29 952
Total TVA
5 391
AIRSI
1 767
Taxe du timbre
100
Grand Total
37 211
Points Caisses : 0,182`;

describe("FBO+ Siège", () => {
  it("lit les vraies factures du Burkina et de Côte d'Ivoire : lignes, taxes, total, points", async () => {
    const correspondant = await compte("correspondant", { pays: GN });
    const idForever = `224${Math.floor(Math.random() * 1e9).toString().padStart(9, "0")}`;
    const ref = adminDb().collection("lotsValidation").doc();
    await ref.set({
      statut: "au_siege", pays: GN, fboId: "f", idForever, nom: "AWA TRAORE", montant: 103000, ccTotal: 0.52, commandes: [],
      lignes: [
        { ref: "621", produitId: "ivision", quantite: 2, prixUnitaire: 32000, cc: 0.15 },
        { ref: "376", produitId: "articsea", quantite: 2, prixUnitaire: 27000, cc: 0.11 },
      ],
    });
    const bf = await correspondant.appeler("lireRecu", { texte: FACTURE_BF(idForever) });
    expect(bf).toMatchObject({ numero: "2026-01-05-100001", total: 103209, sousTotalHT: 85584, tva: 15405, airsi: 2020, timbre: 200, pointsCaisse: 0.52 });
    expect(bf.lignes).toHaveLength(2);
    expect(bf.lots[0]).toMatchObject({ id: ref.id, ecart: 209, ecartCc: 0 });
    // Prix réels, taxes comprises : 23 040 × (103 209 / 85 584).
    expect(bf.lots[0].prixFactures).toEqual({ ivision: Math.round(23040 * 103209 / 85584), articsea: Math.round(19752 * 103209 / 85584) });

    const ci = await correspondant.appeler("lireRecu", { texte: FACTURE_CI(idForever) });
    expect(ci).toMatchObject({ numero: "0101010A26000000001", total: 37211, sousTotalHT: 29952, tva: 5391, airsi: 1767, timbre: 100, pointsCaisse: 0.182 });
    expect(ci.lignes.map((l: { ref: string; quantite: number }) => [l.ref, l.quantite])).toEqual([["188", 1], ["376", 1]]);
  });

  it("lit l'ID Forever, le total, la TVA et le numéro du reçu, et retrouve le lot", async () => {
    const correspondant = await compte("correspondant", { pays: GN });
    const idForever = `224${Math.floor(Math.random() * 1e9).toString().padStart(9, "0")}`;
    const id = await lot(idForever, 25960);
    await lot(`224999${Math.floor(Math.random() * 1e6)}`, 10000); // autre distributeur : ignoré
    const groupe = `${idForever.slice(0, 3)}-${idForever.slice(3, 6)}-${idForever.slice(6, 9)}-${idForever.slice(9)}`;
    const r = await correspondant.appeler("lireRecu", { texte: RECU(groupe, "25 960") });
    expect(r).toMatchObject({ total: 25960, tva: 3960, numero: "FLP-20261112-0042" });
    expect(r.idsForever).toContain(idForever);
    expect(r.lots.map((l: { id: string }) => l.id)).toEqual([id]);
    expect(r.lots[0]).toMatchObject({ nom: "Mamadou Diallo", ecart: 0 });
  });

  it("dépose les lots au siège, puis valide avec la facture et le reçu ; réservé au siège du pays", async () => {
    const correspondant = await compte("correspondant", { pays: GN });
    const id = await lot(`224${Math.floor(Math.random() * 1e9).toString().padStart(9, "0")}`, 22000);
    for (const intrus of [await compte("fbo", { pays: GN }), await compte("livreur", { pays: GN }), await compte("correspondant", { pays: "Sénégal" })]) {
      expect(await codeErreur(intrus.appeler("deposerLotsAuSiege", { ids: [id] }))).toBe("functions/permission-denied");
    }
    expect(await correspondant.appeler("deposerLotsAuSiege", { ids: [id] })).toEqual({ deposes: 1 });
    expect((await adminDb().doc(`lotsValidation/${id}`).get()).data()).toMatchObject({ statut: "au_siege" });
    await correspondant.appeler("validerLotSiege", { id, reference: "FLP-1", montantFacture: 22000, recu: `recus/${GN}/${id}/scan.jpg` });
    expect((await adminDb().doc(`lotsValidation/${id}`).get()).data()).toMatchObject({
      statut: "valide", montantFacture: 22000, recu: `recus/${GN}/${id}/scan.jpg`, reference: "FLP-1",
    });
  });

  it("un reçu illisible est refusé ; le centre d'appel ne lit pas les reçus", async () => {
    const correspondant = await compte("correspondant", { pays: GN });
    expect(await codeErreur(correspondant.appeler("lireRecu", { texte: "abc" }))).toBe("functions/invalid-argument");
    expect(await codeErreur((await compte("operateur", { pays: GN })).appeler("lireRecu", { texte: RECU("224000000000", "1 000") })))
      .toBe("functions/permission-denied");
  });
});
