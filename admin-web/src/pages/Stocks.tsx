import { addDoc, collection } from "firebase/firestore";
import { useState, type FormEvent } from "react";
import { Etat, filtrePays, PAYS, useAction, useCollection, type Doc } from "../commun";
import { appeler, db } from "../firebase";
import { useSession } from "../session";

export default function Stocks() {
  const { role, pays } = useSession();
  const superAdmin = role === "super_admin";
  const { docs: tousEntrepots } = useCollection("warehouses");
  const entrepots = tousEntrepots?.filter((w) => superAdmin || w.pays === pays);
  const { docs: stock, erreur } = useCollection("stock", ...filtrePays(role, pays));
  const { docs: produits } = useCollection("products");
  const [choix, setEntrepotId] = useState("");
  const entrepotId = choix || entrepots?.[0]?.id || "";
  const [paysEntrepot, setPaysEntrepot] = useState(PAYS[0]);
  const [recherche, setRecherche] = useState("");
  const [alertes, setAlertes] = useState(false);
  const [nouvelEntrepot, setNouvelEntrepot] = useState("");
  const action = useAction();

  const nomProduit = new Map(produits?.map((p) => [p.id, p.nom]));
  const lignes = stock
    ?.filter((s) => s.entrepotId === entrepotId)
    .filter((s) => !alertes || s.disponible <= (s.seuilAlerte ?? 10))
    .filter((s) => `${nomProduit.get(s.produitId) ?? ""} ${s.produitId}`.toLowerCase().includes(recherche.toLowerCase()))
    .sort((a, b) => a.disponible - b.disponible) ?? null;

  function ajouterEntrepot(e: FormEvent) {
    e.preventDefault();
    action.lancer(async () => {
      await addDoc(collection(db, "warehouses"), {
        nom: nouvelEntrepot,
        pays: superAdmin ? paysEntrepot : pays,
        actif: true,
      });
      setNouvelEntrepot("");
    });
  }

  return (
    <>
      <h1>Stocks</h1>
      <div className="filtres">
        <select aria-label="Entrepôt" value={entrepotId} onChange={(e) => setEntrepotId(e.target.value)}>
          {entrepots?.map((w) => <option key={w.id} value={w.id}>{w.nom}{superAdmin ? ` (${w.pays})` : ""}</option>)}
        </select>
        <input placeholder="Rechercher un produit" value={recherche} onChange={(e) => setRecherche(e.target.value)} />
        <label className="case">
          <input type="checkbox" checked={alertes} onChange={(e) => setAlertes(e.target.checked)} /> Stock bas uniquement
        </label>
      </div>

      <Etat docs={lignes} erreur={erreur} vide="Aucun stock pour ce filtre." />
      {lignes && lignes.length > 0 && (
        <table>
          <thead><tr><th>Produit</th><th className="droite">Disponible</th><th className="droite">Réservé</th><th>Mouvement</th></tr></thead>
          <tbody>
            {lignes.map((s) => <LigneStock key={s.id} ligne={s} nom={nomProduit.get(s.produitId) ?? s.produitId} />)}
          </tbody>
        </table>
      )}

      {["admin", "super_admin"].includes(role) && (
        <form className="filtres marge" onSubmit={ajouterEntrepot}>
          <input placeholder="Nom du nouvel entrepôt" value={nouvelEntrepot} onChange={(e) => setNouvelEntrepot(e.target.value)} required />
          {superAdmin && (
            <select aria-label="Pays de l'entrepôt" value={paysEntrepot} onChange={(e) => setPaysEntrepot(e.target.value)}>
              {PAYS.map((p) => <option key={p}>{p}</option>)}
            </select>
          )}
          <button className="secondaire" disabled={action.enCours}>Ajouter un entrepôt</button>
          {action.erreur && <span className="erreur">{action.erreur}</span>}
        </form>
      )}
    </>
  );
}

function LigneStock({ ligne, nom }: { ligne: Doc; nom: string }) {
  const [quantite, setQuantite] = useState("");
  const { enCours, erreur, lancer } = useAction();
  const bas = ligne.disponible <= (ligne.seuilAlerte ?? 10);

  const envoyer = (type: "entree" | "inventaire") =>
    lancer(async () => {
      await appeler("ajusterStock", {
        entrepotId: ligne.entrepotId,
        produitId: ligne.produitId,
        type,
        quantite: Number(quantite),
      });
      setQuantite("");
    });

  return (
    <tr>
      <td>{nom}</td>
      <td className={`droite ${bas ? "alerte" : ""}`}>{ligne.disponible}</td>
      <td className="droite">{ligne.reserve}</td>
      <td>
        <div className="actions serre">
          <input
            className="quantite"
            type="number"
            min={0}
            aria-label={`Quantité pour ${nom}`}
            value={quantite}
            onChange={(e) => setQuantite(e.target.value)}
          />
          <button className="secondaire" disabled={enCours || quantite === ""} onClick={() => envoyer("entree")}>Entrée</button>
          <button className="secondaire" disabled={enCours || quantite === ""} onClick={() => envoyer("inventaire")}>Inventaire</button>
        </div>
        {erreur && <span className="erreur">{erreur}</span>}
      </td>
    </tr>
  );
}
