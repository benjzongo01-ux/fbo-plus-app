import { orderBy, where } from "firebase/firestore";
import { useEffect, useState } from "react";
import { dateCourte, Etat, fcfa, filtrePays, LIBELLES_STATUTS, useAction, useCollection, type Doc } from "../commun";
import { appeler } from "../firebase";
import { useSession } from "../session";

const FILTRES = ["a_traiter", "nouvelle", "confirmee", "en_preparation", "prete", "assignee", "en_livraison", "livree", "validee", "expediee", "annulee", "retournee"];
const A_TRAITER = ["nouvelle", "confirmee", "en_preparation", "prete"];
const MODES: Record<string, string> = { livraison: "Livraison", validation: "Validation", expedition: "Expédition" };
const ADMIN = ["admin", "super_admin"];

const ETAPE_SUIVANTE: Record<string, { libelle: string; roles: string[] }> = {
  nouvelle: { libelle: "Confirmer", roles: ["operateur", ...ADMIN] },
  confirmee: { libelle: "Commencer la préparation", roles: ["gestionnaire_stock", ...ADMIN] },
  en_preparation: { libelle: "Marquer prête", roles: ["gestionnaire_stock", ...ADMIN] },
};

export default function Commandes() {
  const [filtre, setFiltre] = useState("a_traiter");
  const [ouverteId, setOuverteId] = useState<string | null>(null);
  const { role, pays } = useSession();
  const { docs, erreur } = useCollection("orders", ...filtrePays(role, pays), orderBy("createdAt", "desc"));

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
          {visibles && visibles.length > 0 && (
            <div className="actions marge">
              <button className="secondaire" onClick={() => exporterCommandes(visibles)}>Exporter en Excel</button>
            </div>
          )}
          <Etat docs={visibles} erreur={erreur} vide="Aucune commande dans cette file." />
          {visibles && visibles.length > 0 && (
            <table>
              <thead><tr><th>Date</th><th>Pour</th><th>Type</th>{role === "super_admin" && <th>Pays</th>}<th>Total</th><th>Statut</th></tr></thead>
              <tbody>
                {visibles.map((c) => (
                  <tr key={c.id} onClick={() => setOuverteId(c.id)} className={c.id === ouverteId ? "selection" : ""}>
                    <td>{dateCourte(c.createdAt)}</td>
                    <td>{nomCommande(c)}</td>
                    <td>{MODES[c.mode] ?? "Livraison"}{c.expedition ? ` · ${c.expedition.ville}` : ""}</td>
                    {role === "super_admin" && <td>{c.pays ?? "Burkina Faso"}</td>}
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

interface Proposition {
  livreurId: string;
  nom: string;
  score: number;
  raisons: string[];
}

/** Livreurs classés par l'affectation intelligente (charge, quartier, distance, fiabilité). */
function Propositions({ commandeId, statut }: { commandeId: string; statut: string }) {
  const [propositions, setPropositions] = useState<Proposition[] | null>(null);
  const { enCours, erreur, lancer } = useAction();
  useEffect(() => {
    let actif = true;
    appeler<{ propositions: Proposition[] }>("proposerLivreurs", { id: commandeId })
      .then((r) => actif && setPropositions(r.propositions))
      .catch(() => actif && setPropositions([]));
    return () => { actif = false; };
  }, [commandeId, statut]);

  if (!propositions?.length) return null;
  return (
    <div className="encadre marge">
      <div className="discret">Livreurs conseillés</div>
      <ol className="propositions">
        {propositions.slice(0, 3).map((p) => (
          <li key={p.livreurId}><strong>{p.nom}</strong> <span className="discret">· {p.raisons.join(" · ")}</span></li>
        ))}
      </ol>
      {statut === "prete" && (
        <button
          className="principal"
          disabled={enCours}
          onClick={() => lancer(() => appeler("affecterMeilleurLivreur", { id: commandeId }))}
        >
          Affecter le meilleur : {propositions[0].nom}
        </button>
      )}
      {erreur && <p className="erreur">{erreur}</p>}
    </div>
  );
}

const POINTS_CONTROLE: [string, string][] = [
  ["produits", "Bons produits"],
  ["quantites", "Bonnes quantités"],
  ["peremption", "Dates de péremption vérifiées"],
  ["emballage", "Emballage en bon état"],
];

const nomBeneficiaire = (c: Doc) =>
  [c.beneficiaire?.prenom, c.beneficiaire?.nom].filter(Boolean).join(" ");

const nomCommande = (c: Doc) =>
  [c.numero ? `N° ${c.numero}` : "", c.client?.nom || nomBeneficiaire(c) || (c.numero ? "" : c.titre)].filter(Boolean).join(" · ");

function DetailCommande({ commande }: { commande: Doc }) {
  const { role } = useSession();
  const { enCours, erreur, lancer } = useAction();
  const [quantites, setQuantites] = useState<Record<string, number>>(() =>
    Object.fromEntries(commande.lignes.map((l: Doc) => [l.produitId, l.quantite])),
  );
  const { docs: livreurs } = useCollection(
    "users",
    where("role", "==", "livreur"),
    where("pays", "==", commande.pays ?? "Burkina Faso"),
  );
  const validation = commande.mode === "validation";
  const expedition = commande.mode === "expedition";
  const [frais, setFrais] = useState(commande.fraisAFixer ? "" : String(commande.fraisLivraison ?? 0));
  const [compagnie, setCompagnie] = useState(commande.expedition?.compagnie ?? "");
  const [numeroColis, setNumeroColis] = useState("");
  const [livreurId, setLivreurId] = useState("");
  const [motif, setMotif] = useState<string | null>(null);
  // Contrôle qualité exigé par le serveur avant « prête ».
  const [controle, setControle] = useState<Record<string, boolean>>({});
  const controleComplet = POINTS_CONTROLE.every(([cle]) => controle[cle]);

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
      <h2>{nomCommande(commande)}</h2>
      <p>
        <strong>{validation ? "Validation sur l'ID" : expedition ? "Expédition" : "Livraison"}</strong> · {commande.pays ?? "Burkina Faso"}
      </p>
      {commande.beneficiaire && (
        <div className="encadre">
          <div className="discret">
            {commande.beneficiaire.type === "filleul" ? "Commande pour un filleul" : "Commande pour le distributeur"}
          </div>
          <div>{nomBeneficiaire(commande)}</div>
          <div>ID Forever : <strong>{commande.beneficiaire.idForever}</strong></div>
        </div>
      )}
      {commande.expedition && (
        <div className="encadre">
          <div className="discret">Expédition</div>
          <div>Vers : <strong>{commande.expedition.ville}</strong></div>
          <div>Compagnie : {commande.expedition.compagnie || <em>à choisir par l'expéditeur</em>}</div>
          {commande.expedition.numeroColis && <div>N° du colis : {commande.expedition.numeroColis}</div>}
          <div>Frais : {fcfa(commande.expedition.frais)}</div>
        </div>
      )}
      {commande.client && (
        <p>
          {expedition && <span className="discret">Destinataire<br /></span>}
          {commande.client.nom}
          {commande.client.telephone && <><br /><a href={`tel:${commande.client.telephone}`}>{commande.client.telephone}</a></>}
          {commande.client.telephone2 && <><br /><a href={`tel:${commande.client.telephone2}`}>{commande.client.telephone2}</a></>}
          {commande.adresse && <><br />{commande.adresse}</>}
        </p>
      )}
      <p><span className={`statut ${commande.statut}`}>{LIBELLES_STATUTS[commande.statut]}</span></p>
      {commande.note && <p className="encadre">Note du distributeur : {commande.note}</p>}
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
        {commande.fraisAFixer && <><span className="alerte">Frais de {expedition ? "l'expédition" : "livraison"} à fixer selon le lieu</span><br /></>}
        {commande.fraisLivraison > 0 && <>{validation ? "Frais de validation" : expedition ? "Expédition" : "Livraison"} : {fcfa(commande.fraisLivraison)}<br /></>}
        {validation && commande.prixValidation > 0 && <>Prix de validation : {fcfa(commande.prixValidation)}<br /></>}
        <strong>Total : {fcfa(commande.total)}</strong>
      </p>

      {erreur && <p className="erreur">{erreur}</p>}

      <div className="actions">
        {change && <button className="principal" disabled={enCours} onClick={enregistrer}>Enregistrer les quantités</button>}
        {!change && etape?.roles.includes(role) && commande.statut === "en_preparation" && (
          <div className="encadre controle">
            <div className="discret">Contrôle qualité avant de marquer prête</div>
            {POINTS_CONTROLE.map(([cle, libelle]) => (
              <label key={cle} className="case">
                <input type="checkbox" checked={!!controle[cle]} onChange={(e) => setControle({ ...controle, [cle]: e.target.checked })} />
                {libelle}
              </label>
            ))}
          </div>
        )}
        {!change && etape?.roles.includes(role) && (
          <button
            className="principal"
            disabled={enCours || (commande.statut === "en_preparation" && !controleComplet) || (commande.statut === "nouvelle" && commande.fraisAFixer)}
            onClick={() => lancer(() => appeler("avancerCommande", { id: commande.id, controle }))}
          >
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

      {centreAppel && validation && commande.statut === "prete" && (
        <div className="actions">
          <button
            className="principal"
            disabled={enCours}
            onClick={() => lancer(() => appeler("validerSurId", { id: commande.id }))}
          >
            Valider sur l'ID {commande.beneficiaire?.idForever}
          </button>
        </div>
      )}

      {centreAppel && (expedition || commande.mode === "livraison") && ["nouvelle", "confirmee"].includes(commande.statut) && (
        <div className="actions">
          <label>
            {expedition ? "Frais d'expédition selon la ville (FCFA)" : "Frais de livraison selon le lieu (FCFA, dans la fourchette du pays)"}
            <input type="number" min={0} value={frais} onChange={(e) => setFrais(e.target.value)} />
          </label>
          <button
            className="secondaire"
            disabled={enCours || Number(frais) === commande.fraisLivraison || !/^\d+$/.test(frais)}
            onClick={() => lancer(() => appeler("fixerFraisExpedition", { id: commande.id, frais: Number(frais) }))}
          >
            Ajuster les frais
          </button>
        </div>
      )}

      {centreAppel && expedition && commande.statut === "prete" && (
        <div className="actions">
          <input
            placeholder="Compagnie de transport"
            aria-label="Compagnie de transport"
            value={compagnie}
            onChange={(e) => setCompagnie(e.target.value)}
          />
          <input
            placeholder="N° du colis (facultatif)"
            aria-label="Numéro du colis"
            value={numeroColis}
            onChange={(e) => setNumeroColis(e.target.value)}
          />
          <button
            className="principal"
            disabled={enCours || !compagnie.trim()}
            onClick={() => lancer(() => appeler("expedierCommande", { id: commande.id, compagnie, numeroColis }))}
          >
            Marquer expédiée
          </button>
        </div>
      )}

      {centreAppel && !validation && !expedition && ["prete", "assignee"].includes(commande.statut) && (
        <Propositions commandeId={commande.id} statut={commande.statut} />
      )}

      {centreAppel && !validation && !expedition && ["prete", "assignee"].includes(commande.statut) && (
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

/** Commandes affichées, en fichier CSV lisible par Excel. */
function exporterCommandes(commandes: Doc[]) {
  const champ = (v: unknown) => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const lignes = [["Date", "Pour", "ID Forever", "Type", "Pays", "Client", "Téléphone", "Adresse", "Produits", "Total", "CC", "Statut"].join(";")];
  for (const c of commandes) {
    lignes.push(
      [
        dateCourte(c.createdAt), nomCommande(c), c.beneficiaire?.idForever, c.mode ?? "livraison", c.pays,
        c.client?.nom, c.client?.telephone, c.adresse,
        (c.lignes ?? []).map((l: Doc) => `${l.nom} x${l.quantite}`).join(", "),
        c.total, c.ccTotal, LIBELLES_STATUTS[c.statut] ?? c.statut,
      ].map(champ).join(";"),
    );
  }
  const fichier = new Blob(["﻿" + lignes.join("\r\n")], { type: "text/csv;charset=utf-8" });
  const lien = document.createElement("a");
  lien.href = URL.createObjectURL(fichier);
  lien.download = `commandes-${new Date().toISOString().slice(0, 10)}.csv`;
  lien.click();
  URL.revokeObjectURL(lien.href);
}
