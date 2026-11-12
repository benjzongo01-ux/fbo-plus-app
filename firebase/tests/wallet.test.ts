import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { adminDb, cle, codeErreur, compte, fermerApplis, preparerAdmin } from "./outils.js";

beforeAll(preparerAdmin);
afterAll(fermerApplis);

const wallet = async (uid: string) => (await adminDb().doc(`wallets/${uid}`).get()).data() ?? {};

/** Dépôt simulé confirmé : crédite le wallet. */
async function deposer(u: Awaited<ReturnType<typeof compte>>, montant: number) {
  const { id } = await u.appeler("demarrerDepot", {
    cleIdempotence: cle(), montant, operateur: "Orange Money", numero: "70 12 34 56",
  });
  return u.appeler("confirmerDepotSimule", { id, succes: true });
}

describe("wallet : dépôts", () => {
  it("crédite le wallet seulement quand le paiement est confirmé", async () => {
    const u = await compte("fbo");
    const { id, statut } = await u.appeler("demarrerDepot", {
      cleIdempotence: cle(), montant: 10000, operateur: "Orange Money", numero: "+226 70 12 34 56",
    });
    expect(statut).toBe("en_attente");
    expect((await wallet(u.uid)).solde ?? 0).toBe(0);
    expect(await u.appeler("confirmerDepotSimule", { id, succes: true })).toMatchObject({ statut: "confirme", solde: 10000 });
    // Une deuxième confirmation ne crédite pas deux fois.
    await u.appeler("confirmerDepotSimule", { id, succes: true });
    expect(await wallet(u.uid)).toMatchObject({ solde: 10000, bloque: 0, totalEntrees: 10000, pays: "Burkina Faso" });
    const mouvements = await adminDb().collection(`wallets/${u.uid}/mouvements`).get();
    expect(mouvements.docs.map((d) => d.data())).toEqual([expect.objectContaining({ type: "depot", montant: 10000, soldeApres: 10000 })]);
  });

  it("un paiement refusé ne crédite rien ; montant, opérateur et numéro sont vérifiés", async () => {
    const u = await compte("fbo");
    const { id } = await u.appeler("demarrerDepot", { cleIdempotence: cle(), montant: 2000, operateur: "Moov Money", numero: "60123456" });
    expect(await u.appeler("confirmerDepotSimule", { id, succes: false })).toEqual({ statut: "echoue" });
    expect((await wallet(u.uid)).solde ?? 0).toBe(0);
    const base = { cleIdempotence: cle(), montant: 2000, operateur: "Orange Money", numero: "70123456" };
    expect(await codeErreur(u.appeler("demarrerDepot", { ...base, montant: 100 }))).toBe("functions/invalid-argument");
    expect(await codeErreur(u.appeler("demarrerDepot", { ...base, operateur: "Wave" }))).toBe("functions/invalid-argument");
    expect(await codeErreur(u.appeler("demarrerDepot", { ...base, numero: "7012" }))).toBe("functions/invalid-argument");
  });

  it("le personnel n'a pas de wallet ; on ne confirme pas le dépôt d'un autre", async () => {
    const operateur = await compte("operateur");
    expect(await codeErreur(operateur.appeler("demarrerDepot", {
      cleIdempotence: cle(), montant: 2000, operateur: "Orange Money", numero: "70123456",
    }))).toBe("functions/permission-denied");
    const a = await compte("fbo");
    const b = await compte("fbo");
    const { id } = await a.appeler("demarrerDepot", { cleIdempotence: cle(), montant: 2000, operateur: "Orange Money", numero: "70123456" });
    expect(await codeErreur(b.appeler("confirmerDepotSimule", { id, succes: true }))).toBe("functions/not-found");
  });
});

describe("wallet : retraits", () => {
  it("bloque le montant, puis l'administration du pays le paie", async () => {
    const u = await compte("fbo");
    await deposer(u, 20000);
    const { id } = await u.appeler("demanderRetrait", { cleIdempotence: cle(), montant: 8000, operateur: "Orange Money", numero: "70123456" });
    expect(await wallet(u.uid)).toMatchObject({ solde: 12000, bloque: 8000 });

    const adminCI = await compte("admin", { pays: "Côte d'Ivoire" });
    expect(await codeErreur(adminCI.appeler("deciderRetrait", { id, decision: "valider" }))).toBe("functions/permission-denied");
    const operateur = await compte("operateur");
    expect(await codeErreur(operateur.appeler("deciderRetrait", { id, decision: "valider" }))).toBe("functions/permission-denied");

    const admin = await compte("admin");
    expect(await admin.appeler("deciderRetrait", { id, decision: "valider" })).toEqual({ statut: "paye" });
    expect(await wallet(u.uid)).toMatchObject({ solde: 12000, bloque: 0, totalSorties: 8000 });
    expect(await codeErreur(admin.appeler("deciderRetrait", { id, decision: "refuser", motif: "x" }))).toBe("functions/failed-precondition");
  });

  it("un retrait refusé rend le montant ; pas de retrait au-delà du solde ni sous le minimum", async () => {
    const u = await compte("livreur");
    await deposer(u, 10000);
    const admin = await compte("admin");
    const { id } = await u.appeler("demanderRetrait", { cleIdempotence: cle(), montant: 10000, operateur: "Moov Money", numero: "60123456" });
    expect(await codeErreur(admin.appeler("deciderRetrait", { id, decision: "refuser" }))).toBe("functions/invalid-argument");
    await admin.appeler("deciderRetrait", { id, decision: "refuser", motif: "Numéro au nom d'un tiers" });
    expect(await wallet(u.uid)).toMatchObject({ solde: 10000, bloque: 0 });
    expect((await adminDb().doc(`retraits/${id}`).get()).data()).toMatchObject({ statut: "refuse", motif: "Numéro au nom d'un tiers" });

    const base = { operateur: "Orange Money", numero: "70123456" };
    expect(await codeErreur(u.appeler("demanderRetrait", { ...base, cleIdempotence: cle(), montant: 15000 }))).toBe("functions/failed-precondition");
    expect(await codeErreur(u.appeler("demanderRetrait", { ...base, cleIdempotence: cle(), montant: 1000 }))).toBe("functions/invalid-argument");
  });

  it("la même demande envoyée deux fois ne retire qu'une fois", async () => {
    const u = await compte("fbo");
    await deposer(u, 20000);
    const demande = { cleIdempotence: cle(), montant: 6000, operateur: "Orange Money", numero: "70123456" };
    await u.appeler("demanderRetrait", demande);
    await u.appeler("demanderRetrait", demande);
    expect(await wallet(u.uid)).toMatchObject({ solde: 14000, bloque: 6000 });
  });
});

describe("wallet : ajustement et lecture", () => {
  it("seul le super administrateur ajuste, avec un motif, sans passer sous zéro", async () => {
    const u = await compte("fbo");
    const admin = await compte("admin");
    const sa = await compte("super_admin");
    expect(await codeErreur(admin.appeler("ajusterWallet", { uid: u.uid, montant: 5000, motif: "Geste" }))).toBe("functions/permission-denied");
    expect(await codeErreur(sa.appeler("ajusterWallet", { uid: u.uid, montant: 5000, motif: "" }))).toBe("functions/invalid-argument");
    expect(await sa.appeler("ajusterWallet", { uid: u.uid, montant: 5000, motif: "Bonus de lancement" })).toEqual({ solde: 5000 });
    expect(await codeErreur(sa.appeler("ajusterWallet", { uid: u.uid, montant: -6000, motif: "Correction" }))).toBe("functions/failed-precondition");
    const journal = await adminDb().collection("auditLogs").where("cibleId", "==", u.uid).where("action", "==", "wallet_ajuste").get();
    expect(journal.size).toBe(1);
  });
});
