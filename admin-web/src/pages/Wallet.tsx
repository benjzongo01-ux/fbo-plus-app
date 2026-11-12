import { where } from "firebase/firestore";
import { useState } from "react";
import { dateCourte, Etat, fcfa, filtrePays, LIBELLES_ROLES, useAction, useCollection, type Doc } from "../commun";
import { appeler } from "../firebase";
import { useSession } from "../session";

const STATUTS: Record<string, string> = { en_attente: "En attente", paye: "Payé", refuse: "Refusé" };

/**
 * Wallets des distributeurs et des livreurs : demandes de retrait à valider ou refuser,
 * soldes, et ajustement manuel (super administrateur seulement, motivé et journalisé).
 */
export default function Wallet() {
  const { role, pays } = useSession();
  const [onglet, setOnglet] = useState<"retraits" | "soldes" | "historique">("retraits");
  const enAttente = useCollection("retraits", ...filtrePays(role, pays), where("statut", "==", "en_attente"));
  const tous = useCollection("retraits", ...filtrePays(role, pays));
  const wallets = useCollection("wallets", ...filtrePays(role, pays));

  const parDate = (a: Doc, b: Doc) => (b.creeLe?.toMillis?.() ?? 0) - (a.creeLe?.toMillis?.() ?? 0);
  const aTraiter = enAttente.docs ? [...enAttente.docs].sort(parDate).reverse() : null;
  const historique = tous.docs ? tous.docs.filter((d) => d.statut !== "en_attente").sort(parDate) : null;
  const soldes = wallets.docs ? [...wallets.docs].sort((a, b) => (b.solde ?? 0) - (a.solde ?? 0)) : null;
  const totalSoldes = soldes?.reduce((s, w) => s + (w.solde ?? 0), 0) ?? 0;
  const totalAttente = aTraiter?.reduce((s, r) => s + (r.montant ?? 0), 0) ?? 0;

  return (
    <>
      <h1>Wallet</h1>
      <p className="discret">
        Les soldes sont tenus par le serveur. Un retrait validé part vers le numéro mobile money indiqué ;
        refusé, son montant revient dans le wallet. Mode test : les versements sont simulés.
      </p>
      <div className="grille-stats marge">
        <div className="carte"><div className="discret">Retraits à traiter</div><strong>{aTraiter?.length ?? "…"}</strong> <span className="discret">· {fcfa(totalAttente)}</span></div>
        <div className="carte"><div className="discret">Total des soldes</div><strong>{fcfa(totalSoldes)}</strong></div>
        <div className="carte"><div className="discret">Wallets ouverts</div><strong>{soldes?.length ?? "…"}</strong></div>
      </div>
      <div className="onglets">
        <button className={onglet === "retraits" ? "actif" : ""} onClick={() => setOnglet("retraits")}>Retraits à traiter</button>
        <button className={onglet === "soldes" ? "actif" : ""} onClick={() => setOnglet("soldes")}>Soldes</button>
        <button className={onglet === "historique" ? "actif" : ""} onClick={() => setOnglet("historique")}>Retraits traités</button>
      </div>

      {onglet === "retraits" && (
        <>
          <Etat docs={aTraiter} erreur={enAttente.erreur} vide="Aucune demande de retrait en attente." />
          {aTraiter && aTraiter.length > 0 && (
            <table>
              <thead><tr><th>Demandé le</th><th>Titulaire</th><th>Vers</th><th className="droite">Montant</th><th /></tr></thead>
              <tbody>{aTraiter.map((r) => <LigneRetrait key={r.id} retrait={r} />)}</tbody>
            </table>
          )}
        </>
      )}

      {onglet === "soldes" && (
        <>
          <Etat docs={soldes} erreur={wallets.erreur} vide="Aucun wallet ouvert pour l'instant." />
          {soldes && soldes.length > 0 && (
            <table>
              <thead><tr><th>Titulaire</th><th>Rôle</th><th>Pays</th><th className="droite">Disponible</th><th className="droite">Retrait en cours</th><th /></tr></thead>
              <tbody>{soldes.map((w) => <LigneSolde key={w.id} wallet={w} peutAjuster={role === "super_admin"} />)}</tbody>
            </table>
          )}
        </>
      )}

      {onglet === "historique" && (
        <>
          <Etat docs={historique} erreur={tous.erreur} vide="Aucun retrait traité." />
          {historique && historique.length > 0 && (
            <table>
              <thead><tr><th>Décidé le</th><th>Titulaire</th><th>Vers</th><th className="droite">Montant</th><th>État</th></tr></thead>
              <tbody>
                {historique.map((r) => (
                  <tr key={r.id}>
                    <td>{dateCourte(r.decideLe)}</td>
                    <td>{r.nom || "—"}</td>
                    <td>{r.operateur} · {r.numero}</td>
                    <td className="droite">{fcfa(r.montant)}</td>
                    <td>
                      <span className={`statut ${r.statut === "paye" ? "validee" : "annulee"}`}>{STATUTS[r.statut] ?? r.statut}</span>
                      {r.motif && <div className="discret">{r.motif}</div>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </>
  );
}

function LigneRetrait({ retrait }: { retrait: Doc }) {
  const { enCours, erreur, lancer } = useAction();
  return (
    <tr>
      <td>{dateCourte(retrait.creeLe)}</td>
      <td>{retrait.nom || "—"} <span className="discret">· {LIBELLES_ROLES[retrait.role] ?? retrait.role}</span></td>
      <td>{retrait.operateur} · {retrait.numero}</td>
      <td className="droite"><strong>{fcfa(retrait.montant)}</strong></td>
      <td className="droite">
        <button
          className="principal"
          disabled={enCours}
          onClick={() => {
            if (window.confirm(`Payer ${fcfa(retrait.montant)} sur ${retrait.operateur} ${retrait.numero} ?`)) {
              lancer(() => appeler("deciderRetrait", { id: retrait.id, decision: "valider" }));
            }
          }}
        >
          Valider
        </button>{" "}
        <button
          className="secondaire"
          disabled={enCours}
          onClick={() => {
            const motif = window.prompt("Motif du refus (le montant revient dans le wallet) :");
            if (motif && motif.trim()) lancer(() => appeler("deciderRetrait", { id: retrait.id, decision: "refuser", motif }));
          }}
        >
          Refuser
        </button>
        {erreur && <div className="erreur">{erreur}</div>}
      </td>
    </tr>
  );
}

function LigneSolde({ wallet, peutAjuster }: { wallet: Doc; peutAjuster: boolean }) {
  const { enCours, erreur, lancer } = useAction();
  return (
    <tr>
      <td>{wallet.nom || wallet.id}</td>
      <td>{LIBELLES_ROLES[wallet.role] ?? wallet.role ?? "—"}</td>
      <td>{wallet.pays ?? "—"}</td>
      <td className="droite"><strong>{fcfa(wallet.solde)}</strong></td>
      <td className="droite">{wallet.bloque ? fcfa(wallet.bloque) : "—"}</td>
      <td className="droite">
        {peutAjuster && (
          <button
            className="secondaire"
            disabled={enCours}
            onClick={() => {
              const saisie = window.prompt("Montant à ajouter (négatif pour retirer), en FCFA :");
              const montant = Number(saisie);
              if (!saisie || !Number.isInteger(montant) || montant === 0) return;
              const motif = window.prompt("Motif de l'ajustement (journalisé) :");
              if (motif && motif.trim()) lancer(() => appeler("ajusterWallet", { uid: wallet.id, montant, motif }));
            }}
          >
            Ajuster
          </button>
        )}
        {erreur && <div className="erreur">{erreur}</div>}
      </td>
    </tr>
  );
}
