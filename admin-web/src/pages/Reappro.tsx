import { orderBy } from "firebase/firestore";
import { useState } from "react";
import { dateCourte, Etat, filtrePays, useAction, useCollection, type Doc } from "../commun";
import { appeler } from "../firebase";
import { useSession } from "../session";

const LIBELLES: Record<string, string> = { a_traiter: "À commander", commandee: "Commandée", recue: "Reçue" };

/** Produits passés sous leur seuil d'alerte : à commander, puis à réceptionner dans le stock. */
export default function Reappro() {
  const { role, pays } = useSession();
  const [voirRecues, setVoirRecues] = useState(false);
  const { docs, erreur } = useCollection("reappros", ...filtrePays(role, pays), orderBy("updatedAt", "desc"));
  const visibles = docs?.filter((d) => voirRecues || d.statut !== "recue") ?? null;

  return (
    <>
      <h1>Réapprovisionnement</h1>
      <p className="discret">Une demande s'ouvre seule dès qu'un produit passe sous son seuil d'alerte dans un entrepôt.</p>
      <label className="case marge">
        <input type="checkbox" checked={voirRecues} onChange={(e) => setVoirRecues(e.target.checked)} />
        Voir les demandes déjà reçues
      </label>
      <Etat docs={visibles} erreur={erreur} vide="Aucun produit à réapprovisionner." />
      {visibles && visibles.length > 0 && (
        <table>
          <thead>
            <tr><th>Produit</th><th>Entrepôt</th><th className="droite">Disponible</th><th className="droite">Seuil</th><th className="droite">Conseillé</th><th>État</th><th /></tr>
          </thead>
          <tbody>{visibles.map((d) => <Ligne key={d.id} demande={d} />)}</tbody>
        </table>
      )}
    </>
  );
}

function Ligne({ demande }: { demande: Doc }) {
  const { enCours, erreur, lancer } = useAction();
  return (
    <tr>
      <td>{demande.nom}</td>
      <td>{demande.entrepotId}</td>
      <td className="droite">{demande.disponible}</td>
      <td className="droite">{demande.seuil}</td>
      <td className="droite">{demande.quantiteConseillee}</td>
      <td>{LIBELLES[demande.statut] ?? demande.statut} <span className="discret">· {dateCourte(demande.updatedAt)}</span></td>
      <td className="droite">
        {demande.statut === "a_traiter" && (
          <button className="secondaire" disabled={enCours} onClick={() => lancer(() => appeler("suivreReappro", { id: demande.id, etape: "commandee" }))}>
            Commandée
          </button>
        )}
        {demande.statut === "commandee" && (
          <button
            className="principal"
            disabled={enCours}
            onClick={() => {
              const saisie = window.prompt(`Quantité de ${demande.nom} reçue :`, String(demande.quantiteConseillee));
              const quantite = Number(saisie);
              if (saisie && Number.isInteger(quantite) && quantite > 0) {
                lancer(() => appeler("suivreReappro", { id: demande.id, etape: "recue", quantite }));
              }
            }}
          >
            Reçue
          </button>
        )}
        {erreur && <span className="erreur">{erreur}</span>}
      </td>
    </tr>
  );
}
