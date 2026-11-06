import { orderBy, where } from "firebase/firestore";
import { useState } from "react";
import { dateCourte, Etat, fcfa, LIBELLES_STATUTS, useAction, useCollection, type Doc } from "../commun";
import { appeler } from "../firebase";
import { useSession } from "../session";

const FILTRES = ["a_traiter", "nouvelle", "confirmee", "en_preparation", "prete", "assignee", "en_livraison", "livree", "annulee", "retournee"];
const A_TRAITER = ["nouvelle", "confirmee", "en_preparation", "prete"];
const ADMIN = ["admin", "super_admin"];

const ETAPE_SUIVANTE: Record<string, { libelle: string; roles: string[] }> = {
  nouvelle: { libelle: "Confirmer", roles: ["operateur", ...ADMIN] },
  confirmee: { libelle: "Commencer la préparation", roles: ["gestionnaire_stock", ...ADMIN] },
  en_preparation: { libelle: "Marquer prête", roles: ["gestionnaire_stock", ...ADMIN] },
};

export default function Commandes() {
  const [filtre, setFiltre] = useState("a_traiter");
  const [ouverteId, setOuverteId] = useState<string | null>(null);
  const { docs, erreur } = useCollection("orders", orderBy("createdAt", "desc"));

  const visibles = docs?.filter((c) =>
    filtre === "a_traiter" ? A_TRAITER.includes(c.statut) : c.statut === filtre,
  ) ?? null;
  const ouverte = docs?.find((c) => c.id === ouverteId);

  return (
    <>
      <h1>Centre d'appel</h1>
      <div className="onglets">
        {FILTRES.map((f) => (
          <button key={f} className={f === filtre ? "actif" : ""} onClick={() => setFiltre(f)}>
            {f === "a_traiter" ? "À traiter" : LIBELLES_STATUTS[f]}
          </button>
        ))}
      </div>
      <div className="deux-colonnes">
        <div>
          <Etat docs={visibles} erreur={erreur} vide="Aucune commande dans cette file." />
          {visibles && visibles.length > 0 && (
            <table>
              <thead><tr><th>Date</th><th>Client</th><th>Total</th><th>Statut</th></tr></thead>
              <tbody>
                {visibles.map((c) => (
                  <tr key={c.id} onClick={() => setOuverteId(c.id)} className={c.id === ouverteId ? "selection" : ""}>
                    <td>{dateCourte(c.createdAt)}</td>
                    <td>{c.client?.nom || c.titre}</td>
                    <td>{fcfa(c.total)}</td>
                    <td><span className={`statut ${c.statut}`}>{LIBELLES_STATUTS[c.statut]}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        {ouverte && <DetailCommande key={ouverte.id} commande={ouverte} />}
      </div>
    </>
  );
}

function DetailCommande({ commande }: { commande: Doc }) {
  const { role } = useSession();
  const { enCours, erreur, lancer } = useAction();
  const [quantites, setQuantites] = useState<Record<string, number>>(() =>
    Object.fromEntries(commande.lignes.map((l: Doc) => [l.produitId, l.quantite])),
  );
  const { docs: livreurs } = useCollection("users", where("role", "==", "livreur"));
  const [livreurId, setLivreurId] = useState("");
  const [motif, setMotif] = useState<string | null>(null);

  const centreAppel = ["operateur", ...ADMIN].includes(role);
  const modifiable = centreAppel && ["nouvelle", "confirmee"].includes(commande.statut);
  const annulable = centreAppel && ["nouvelle", "confirmee", "en_preparation", "prete", "assignee"].includes(commande.statut);
  const etape = ETAPE_SUIVANTE[commande.statut];
  const change = commande.lignes.some((l: Doc) => quantites[l.produitId] !== l.quantite);

  const enregistrer = () =>
    lancer(() =>
      appeler("modifierCommande", {
        id: commande.id,
        reduction: commande.reduction,
        lignes: commande.lignes
          .map((l: Doc) => ({ produitId: l.produitId, quantite: quantites[l.produitId] }))
          .filter((l: Doc) => l.quantite > 0),
      }),
    );

  return (
    <section className="carte detail">
      <h2>{commande.client?.nom || commande.titre}</h2>
      <p>
        {commande.client?.telephone && <a href={`tel:${commande.client.telephone}`}>{commande.client.telephone}</a>}
        {commande.adresse && <><br />{commande.adresse}</>}
      </p>
      <p><span className={`statut ${commande.statut}`}>{LIBELLES_STATUTS[commande.statut]}</span></p>
      {commande.motifAnnulation && <p className="discret">Motif : {commande.motifAnnulation}</p>}
      {commande.motifRetour && <p className="discret">Motif : {commande.motifRetour}</p>}

      <table>
        <tbody>
          {commande.lignes.map((l: Doc) => (
            <tr key={l.produitId}>
              <td>{l.nom}</td>
              <td className="droite">
                {modifiable ? (
                  <input
                    className="quantite"
                    type="number"
                    min={0}
                    aria-label={`Quantité de ${l.nom}`}
                    value={quantites[l.produitId]}
                    onChange={(e) => setQuantites({ ...quantites, [l.produitId]: Number(e.target.value) })}
                  />
                ) : `× ${l.quantite}`}
              </td>
              <td className="droite">{fcfa(l.prixUnitaire * l.quantite)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="droite">
        {commande.reduction > 0 && <>Réduction : -{fcfa(commande.reduction)}<br /></>}
        {commande.fraisLivraison > 0 && <>Livraison : {fcfa(commande.fraisLivraison)}<br /></>}
        <strong>Total : {fcfa(commande.total)}</strong>
      </p>

      {erreur && <p className="erreur">{erreur}</p>}

      <div className="actions">
        {change && <button className="principal" disabled={enCours} onClick={enregistrer}>Enregistrer les quantités</button>}
        {!change && etape?.roles.includes(role) && (
          <button className="principal" disabled={enCours} onClick={() => lancer(() => appeler("avancerCommande", { id: commande.id }))}>
            {etape.libelle}
          </button>
        )}
        {annulable && motif === null && (
          <button className="danger" disabled={enCours} onClick={() => setMotif("")}>Annuler</button>
        )}
        {enCours && <span className="discret">Envoi en cours…</span>}
      </div>

      {motif !== null && (
        <div className="actions">
          <input
            placeholder="Motif de l'annulation"
            aria-label="Motif de l'annulation"
            value={motif}
            onChange={(e) => setMotif(e.target.value)}
            autoFocus
          />
          <button
            className="danger"
            disabled={enCours || !motif.trim()}
            onClick={() => lancer(() => appeler("annulerCommande", { id: commande.id, motif }))}
          >
            Confirmer l'annulation
          </button>
          <button className="lien" onClick={() => setMotif(null)}>Retour</button>
        </div>
      )}

      {centreAppel && ["prete", "assignee"].includes(commande.statut) && (
        <div className="actions">
          <select aria-label="Livreur" value={livreurId} onChange={(e) => setLivreurId(e.target.value)}>
            <option value="">Choisir un livreur</option>
            {livreurs?.filter((l) => l.statut === "actif").map((l) => (
              <option key={l.id} value={l.id}>{l.nom}</option>
            ))}
          </select>
          <button
            className="principal"
            disabled={enCours || !livreurId}
            onClick={() => lancer(() => appeler("assignerLivreur", { id: commande.id, livreurId }))}
          >
            {commande.statut === "assignee" ? "Réassigner" : "Assigner"}
          </button>
        </div>
      )}
    </section>
  );
}
