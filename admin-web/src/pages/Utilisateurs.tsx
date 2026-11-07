import { useState, type FormEvent } from "react";
import { Etat, filtrePays, LIBELLES_ROLES, PAYS, useAction, useCollection, type Doc } from "../commun";
import { appeler } from "../firebase";
import { useSession } from "../session";

const ROLES_INTERNES = ["livreur", "operateur", "gestionnaire_stock", "admin", "super_admin"];
const ROLES_ADMIN = ["admin", "super_admin"];

export default function Utilisateurs() {
  const { role, utilisateur, pays } = useSession();
  const { docs, erreur } = useCollection("users", ...filtrePays(role, pays));
  const [recherche, setRecherche] = useState("");

  const visibles = docs
    ?.filter((u) => `${u.prenom ?? ""} ${u.nom ?? ""} ${u.telephone ?? ""}`.toLowerCase().includes(recherche.toLowerCase()))
    .sort((a, b) => String(a.role).localeCompare(String(b.role))) ?? null;

  return (
    <>
      <h1>Utilisateurs</h1>
      <NouveauCompte roleAppelant={role} />
      <div className="filtres">
        <input placeholder="Nom ou téléphone" value={recherche} onChange={(e) => setRecherche(e.target.value)} />
      </div>
      <Etat docs={visibles} erreur={erreur} vide="Aucun utilisateur." />
      {visibles && visibles.length > 0 && (
        <table>
          <thead><tr><th>Nom</th><th>Téléphone</th><th>Pays</th><th>Rôle</th><th>Statut</th><th>Actions</th></tr></thead>
          <tbody>
            {visibles.map((u) => (
              <LigneUtilisateur key={u.id} compte={u} roleAppelant={role} moi={u.id === utilisateur?.uid} />
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

function LigneUtilisateur({ compte, roleAppelant, moi }: { compte: Doc; roleAppelant: string; moi: boolean }) {
  const { enCours, erreur, lancer } = useAction();
  const superAdmin = roleAppelant === "super_admin";
  const verrouille = moi || (ROLES_ADMIN.includes(compte.role) && !superAdmin);
  const rolesPossibles = Object.keys(LIBELLES_ROLES).filter((r) => superAdmin || !ROLES_ADMIN.includes(r));

  return (
    <tr>
      <td>{[compte.prenom, compte.nom].filter(Boolean).join(" ") || "—"}{moi && " (vous)"}</td>
      <td>{compte.telephone}</td>
      <td>
        {superAdmin && !moi && compte.role !== "fbo" ? (
          <select
            aria-label={`Pays de ${compte.nom}`}
            value={compte.pays ?? ""}
            disabled={enCours}
            onChange={(e) => lancer(() => appeler("definirPays", { uid: compte.id, pays: e.target.value }))}
          >
            {!compte.pays && <option value="">—</option>}
            {PAYS.map((p) => <option key={p}>{p}</option>)}
          </select>
        ) : (compte.pays || "—")}
      </td>
      <td>
        {verrouille ? LIBELLES_ROLES[compte.role] : (
          <select
            aria-label={`Rôle de ${compte.nom}`}
            value={compte.role}
            disabled={enCours}
            onChange={(e) => lancer(() => appeler("definirRole", { uid: compte.id, role: e.target.value }))}
          >
            {rolesPossibles.map((r) => <option key={r} value={r}>{LIBELLES_ROLES[r]}</option>)}
          </select>
        )}
      </td>
      <td>{compte.statut === "suspendu" ? <span className="alerte">Suspendu</span> : "Actif"}</td>
      <td>
        {!verrouille && (
          <button
            className={compte.statut === "suspendu" ? "secondaire" : "danger"}
            disabled={enCours}
            onClick={() =>
              lancer(() =>
                appeler("definirStatut", {
                  uid: compte.id,
                  statut: compte.statut === "suspendu" ? "actif" : "suspendu",
                }),
              )
            }
          >
            {compte.statut === "suspendu" ? "Réactiver" : "Suspendre"}
          </button>
        )}
        {erreur && <span className="erreur">{erreur}</span>}
      </td>
    </tr>
  );
}

function NouveauCompte({ roleAppelant }: { roleAppelant: string }) {
  const [telephone, setTelephone] = useState("+226");
  const [nom, setNom] = useState("");
  const [role, setRole] = useState("livreur");
  const [pays, setPays] = useState(PAYS[0]);
  const { enCours, erreur, lancer } = useAction();
  const roles = ROLES_INTERNES.filter((r) => roleAppelant === "super_admin" || !ROLES_ADMIN.includes(r));

  async function creer(e: FormEvent) {
    e.preventDefault();
    const ok = await lancer(() => appeler("creerCompteInterne", { telephone, nom, role, pays }));
    if (ok) {
      setTelephone("+226");
      setNom("");
    }
  }

  return (
    <form className="filtres" onSubmit={creer}>
      <input placeholder="Nom complet" value={nom} onChange={(e) => setNom(e.target.value)} required />
      <input aria-label="Téléphone" value={telephone} onChange={(e) => setTelephone(e.target.value)} required />
      <select aria-label="Rôle" value={role} onChange={(e) => setRole(e.target.value)}>
        {roles.map((r) => <option key={r} value={r}>{LIBELLES_ROLES[r]}</option>)}
      </select>
      {roleAppelant === "super_admin" && (
        <select aria-label="Pays" value={pays} onChange={(e) => setPays(e.target.value)}>
          {PAYS.map((p) => <option key={p}>{p}</option>)}
        </select>
      )}
      <button className="principal" disabled={enCours}>Créer le compte</button>
      {erreur && <span className="erreur">{erreur}</span>}
    </form>
  );
}
