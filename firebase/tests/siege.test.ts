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
Reçu N° FLP-20261112-0042
Distributeur : ${id}
Mamadou DIALLO
015 Aloe Vera Gel   2   11 000
Sous-total          22 000
TVA 18%             3 960
TOTAL               ${total} GNF`;

describe("FBO+ Siège", () => {
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
