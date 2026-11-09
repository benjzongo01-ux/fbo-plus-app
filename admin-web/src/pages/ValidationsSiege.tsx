import { orderBy } from "firebase/firestore";
import { useState } from "react";
import { dateCourte, Etat, fcfa, filtrePays, useAction, useCollection, type Doc } from "../commun";
import { appeler } from "../firebase";
import { useSession } from "../session";

const ONGLETS = [
  { cle: "a_valider", libelle: "À valider au siège" },
  { cle: "valide", libelle: "Validés" },
  { cle: "reserves", libelle: "En réserve (sous le minimum)" },
];

/**
 * Lots à valider au siège Forever : ID, nom, téléphone et produits. Le correspondant les exporte
 * (Excel ou impression PDF), les valide au siège, puis les marque « validés » avec la référence du reçu.
 */
export default function ValidationsSiege() {
  const { role, pays } = useSession();
  const [onglet, setOnglet] = useState("a_valider");
  const { docs: lots, erreur } = useCollection("lotsValidation", ...filtrePays(role, pays), orderBy("createdAt", "desc"));
  const { docs: reserves } = useCollection("reservesValidation", ...filtrePays(role, pays));
  const visibles = lots?.filter((l) => l.statut === onglet) ?? null;
  const enReserve = reserves?.filter((r) => (r.lignes?.length ?? 0) > 0) ?? null;

  return (
    <>
      <h1>Validations au siège</h1>
      <p className="discret">
        Les ventes terminées sont regroupées par ID Forever. Un lot part en validation dès l'achat minimum atteint.
      </p>
      <div className="onglets">
        {ONGLETS.map((o) => (
          <button key={o.cle} className={o.cle === onglet ? "actif" : ""} onClick={() => setOnglet(o.cle)}>
            {o.libelle}
            {o.cle === "a_valider" && (lots?.filter((l) => l.statut === "a_valider").length ?? 0) > 0 && (
              <span className="pastille">{lots!.filter((l) => l.statut === "a_valider").length}</span>
            )}
          </button>
        ))}
      </div>

      {onglet === "reserves" ? (
        <Reserves reserves={enReserve} />
      ) : (
        <>
          {onglet === "a_valider" && visibles && visibles.length > 0 && (
            <div className="actions marge">
              <button className="secondaire" onClick={() => exporterExcel(visibles)}>Exporter en Excel</button>
              <button className="secondaire" onClick={() => window.print()}>Imprimer ou PDF</button>
            </div>
          )}
          <Etat docs={visibles} erreur={erreur} vide="Aucun lot dans cette liste." />
          <div className="lots imprimable">
            {visibles?.map((l) => <Lot key={l.id} lot={l} />)}
          </div>
        </>
      )}
    </>
  );
}

function Lot({ lot }: { lot: Doc }) {
  const { enCours, erreur, lancer } = useAction();
  return (
    <section className="carte lot">
      <div className="entete-lot">
        <div>
          <h2>ID Forever : {lot.idForever}</h2>
          <div>{lot.nom}{lot.telephone ? ` · ${lot.telephone}` : ""} · {lot.pays}</div>
          <div className="discret">Créé le {dateCourte(lot.createdAt)} · {lot.commandes?.length ?? 0} commande(s)</div>
        </div>
        <div className="droite">
          <strong>{fcfa(lot.montant)}</strong>
          <div>{lot.ccTotal} CC</div>
        </div>
      </div>
      <table>
        <thead><tr><th>Réf.</th><th>Produit</th><th className="droite">Qté</th><th className="droite">CC</th></tr></thead>
        <tbody>
          {regrouper(lot.lignes).map((l) => (
            <tr key={l.produitId}>
              <td>{l.ref}</td>
              <td>{l.nom}</td>
              <td className="droite">{l.quantite}</td>
              <td className="droite">{Math.round(l.cc * l.quantite * 1000) / 1000}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {lot.statut === "valide" ? (
        <p className="succes">Validé le {dateCourte(lot.valideLe)}{lot.reference ? ` · reçu ${lot.reference}` : ""}</p>
      ) : (
        <div className="actions non-imprime">
          <button
            className="principal"
            disabled={enCours}
            onClick={() => {
              const reference = window.prompt("Référence du reçu de validation au siège (facultatif) :", "");
              if (reference !== null) lancer(() => appeler("validerLotSiege", { id: lot.id, reference }));
            }}
          >
            Marquer validé au siège
          </button>
        </div>
      )}
      {erreur && <p className="erreur">{erreur}</p>}
    </section>
  );
}

function Reserves({ reserves }: { reserves: Doc[] | null }) {
  const { enCours, erreur, lancer } = useAction();
  return (
    <>
      <Etat docs={reserves} erreur="" vide="Aucune vente en attente du minimum." />
      {reserves && reserves.length > 0 && (
        <table>
          <thead><tr><th>ID Forever</th><th>Nom</th><th className="droite">Montant</th><th className="droite">Manque</th><th /></tr></thead>
          <tbody>
            {reserves.map((r) => (
              <tr key={r.id}>
                <td>{r.idForever}</td>
                <td>{r.nom}</td>
                <td className="droite">{fcfa(r.montant)}</td>
                <td className="droite">{fcfa(Math.max(0, (r.minimum ?? 36000) - r.montant))}</td>
                <td className="droite">
                  <button
                    className="lien"
                    disabled={enCours}
                    onClick={() => {
                      if (window.confirm(`Envoyer en validation les ${fcfa(r.montant)} de ${r.nom}, sans attendre le minimum ?`)) {
                        lancer(() => appeler("envoyerReserveEnValidation", { id: r.id }));
                      }
                    }}
                  >
                    Envoyer en validation
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {erreur && <p className="erreur">{erreur}</p>}
    </>
  );
}

/** Un même produit commandé plusieurs fois apparaît sur une seule ligne. */
function regrouper(lignes: Doc[] = []) {
  const parProduit = new Map<string, Doc>();
  for (const l of lignes) {
    const deja = parProduit.get(l.produitId);
    parProduit.set(l.produitId, deja ? { ...deja, quantite: deja.quantite + l.quantite } : { ...l });
  }
  return [...parProduit.values()];
}

/** Fichier CSV lisible par Excel (séparateur « ; » et accents corrects). */
function exporterExcel(lots: Doc[]) {
  const champ = (v: unknown) => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const lignes = [["ID Forever", "Nom", "Téléphone", "Pays", "Réf.", "Produit", "Quantité", "Prix unitaire", "CC"].join(";")];
  for (const lot of lots) {
    for (const l of regrouper(lot.lignes)) {
      lignes.push(
        [lot.idForever, lot.nom, lot.telephone, lot.pays, l.ref, l.nom, l.quantite, l.prixUnitaire, Math.round(l.cc * l.quantite * 1000) / 1000]
          .map(champ)
          .join(";"),
      );
    }
  }
  const fichier = new Blob(["﻿" + lignes.join("\r\n")], { type: "text/csv;charset=utf-8" });
  const lien = document.createElement("a");
  lien.href = URL.createObjectURL(fichier);
  lien.download = `validations-siege-${new Date().toISOString().slice(0, 10)}.csv`;
  lien.click();
  URL.revokeObjectURL(lien.href);
}
