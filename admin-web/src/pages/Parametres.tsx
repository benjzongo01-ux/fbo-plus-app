import { addDoc, collection, deleteDoc, doc, getDoc, onSnapshot, setDoc } from "firebase/firestore";
import { useEffect, useState, type FormEvent } from "react";
import { fcfa, filtrePays, PAYS, useAction, useCollection } from "../commun";
import { db } from "../firebase";
import { PanneauMaintenance } from "../maintenance";
import { useSession } from "../session";

/** Villes où chaque pays expédie, et leurs frais : proposées au distributeur lors de la commande. */
function VillesExpedition() {
  const { role, pays } = useSession();
  const superAdmin = role === "super_admin";
  const action = useAction();
  const { docs: villes } = useCollection("villesExpedition", ...filtrePays(role, pays));
  const [ville, setVille] = useState("");
  const [frais, setFrais] = useState("");
  const [paysVille, setPaysVille] = useState(pays || PAYS[0]);

  function ajouter(e: FormEvent) {
    e.preventDefault();
    action.lancer(async () => {
      await addDoc(collection(db, "villesExpedition"), {
        pays: superAdmin ? paysVille : pays,
        ville: ville.trim(),
        frais: Number(frais),
        actif: true,
      });
      setVille("");
      setFrais("");
    });
  }

  const triees = [...(villes ?? [])].sort((a, b) => `${a.pays}${a.ville}`.localeCompare(`${b.pays}${b.ville}`));

  return (
    <section className="carte etroit">
      <h2>Villes d'expédition</h2>
      <p className="discret">Le distributeur choisit une de ces villes ; ses frais s'ajoutent au total de la commande.</p>
      <ul className="liste">
        {triees.map((v) => (
          <li key={v.id}>
            <span>
              {v.ville}{superAdmin ? ` (${v.pays})` : ""} · {fcfa(v.frais)}
              {!v.actif && <span className="discret"> · fermée</span>}
            </span>
            <span>
              <button
                className="lien"
                onClick={() => {
                  const saisi = window.prompt(`Nouveaux frais pour ${v.ville} (FCFA)`, String(v.frais));
                  if (saisi !== null && /^\d+$/.test(saisi.trim())) {
                    action.lancer(() => setDoc(doc(db, "villesExpedition", v.id), { frais: Number(saisi) }, { merge: true }));
                  }
                }}
              >
                Frais
              </button>{" "}
              <button
                className="lien"
                onClick={() => action.lancer(() => setDoc(doc(db, "villesExpedition", v.id), { actif: !v.actif }, { merge: true }))}
              >
                {v.actif ? "Fermer" : "Rouvrir"}
              </button>
            </span>
          </li>
        ))}
      </ul>
      {action.erreur && <p className="erreur">{action.erreur}</p>}
      <form className="actions" onSubmit={ajouter}>
        <input placeholder="Ville" aria-label="Ville" value={ville} onChange={(e) => setVille(e.target.value)} required />
        <input
          type="number"
          min={0}
          placeholder="Frais (FCFA)"
          aria-label="Frais d'expédition"
          value={frais}
          onChange={(e) => setFrais(e.target.value)}
          required
        />
        {superAdmin && (
          <select aria-label="Pays" value={paysVille} onChange={(e) => setPaysVille(e.target.value)}>
            {PAYS.map((p) => <option key={p}>{p}</option>)}
          </select>
        )}
        <button className="secondaire" disabled={action.enCours}>Ajouter</button>
      </form>
    </section>
  );
}

/** Zones de livraison et leur tarif (remplace le tarif du pays pour cette zone). */
function ZonesLivraison() {
  const { role, pays } = useSession();
  const superAdmin = role === "super_admin";
  const action = useAction();
  const { docs: villes } = useCollection("zonesLivraison", ...filtrePays(role, pays));
  const [ville, setVille] = useState("");
  const [frais, setFrais] = useState("");
  const [paysVille, setPaysVille] = useState(pays || PAYS[0]);

  function ajouter(e: FormEvent) {
    e.preventDefault();
    action.lancer(async () => {
      await addDoc(collection(db, "zonesLivraison"), {
        pays: superAdmin ? paysVille : pays,
        nom: ville.trim(),
        frais: Number(frais),
        actif: true,
      });
      setVille("");
      setFrais("");
    });
  }

  const triees = [...(villes ?? [])].sort((a, b) => `${a.pays}${a.nom}`.localeCompare(`${b.pays}${b.nom}`));

  return (
    <section className="carte etroit">
      <h2>Zones de livraison</h2>
      <p className="discret">Communes ou quartiers dont le tarif diffère du tarif du pays (ex. Abidjan : 3 000 à 5 000 FCFA). Le distributeur choisit la zone à la commande.</p>
      <ul className="liste">
        {triees.map((v) => (
          <li key={v.id}>
            <span>
              {v.nom}{superAdmin ? ` (${v.pays})` : ""} · {fcfa(v.frais)}
              {!v.actif && <span className="discret"> · fermée</span>}
            </span>
            <span>
              <button
                className="lien"
                onClick={() => {
                  const saisi = window.prompt(`Nouveaux frais pour ${v.nom} (FCFA)`, String(v.frais));
                  if (saisi !== null && /^\d+$/.test(saisi.trim())) {
                    action.lancer(() => setDoc(doc(db, "zonesLivraison", v.id), { frais: Number(saisi) }, { merge: true }));
                  }
                }}
              >
                Frais
              </button>{" "}
              <button
                className="lien"
                onClick={() => action.lancer(() => setDoc(doc(db, "zonesLivraison", v.id), { actif: !v.actif }, { merge: true }))}
              >
                {v.actif ? "Fermer" : "Rouvrir"}
              </button>
            </span>
          </li>
        ))}
      </ul>
      {action.erreur && <p className="erreur">{action.erreur}</p>}
      <form className="actions" onSubmit={ajouter}>
        <input placeholder="Zone (commune, quartier)" aria-label="Zone" value={ville} onChange={(e) => setVille(e.target.value)} required />
        <input
          type="number"
          min={0}
          placeholder="Frais (FCFA)"
          aria-label="Frais de livraison de la zone"
          value={frais}
          onChange={(e) => setFrais(e.target.value)}
          required
        />
        {superAdmin && (
          <select aria-label="Pays" value={paysVille} onChange={(e) => setPaysVille(e.target.value)}>
            {PAYS.map((p) => <option key={p}>{p}</option>)}
          </select>
        )}
        <button className="secondaire" disabled={action.enCours}>Ajouter</button>
      </form>
    </section>
  );
}

export default function Parametres() {
  const { role, pays } = useSession();
  const superAdmin = role === "super_admin";
  const [paysTarifs, setPaysTarifs] = useState(pays || PAYS[0]);
  const paysVise = superAdmin ? paysTarifs : pays;
  const [frais, setFrais] = useState("");
  const [commission, setCommission] = useState("");
  const [minimum, setMinimum] = useState("");
  const [enregistre, setEnregistre] = useState(false);
  const action = useAction();
  const { docs: centres } = useCollection("centres");
  const [centre, setCentre] = useState("");

  // Tarifs du pays ; à défaut, les anciens tarifs communs servent de point de départ.
  useEffect(() => {
    setEnregistre(false);
    return onSnapshot(doc(db, "tarifsPays", paysVise), async (d) => {
      const t = d.exists() ? d.data() : (await getDoc(doc(db, "parametres", "tarifs"))).data();
      setFrais(String(t?.fraisLivraison ?? 0));
      setCommission(String(t?.commissionLivreur ?? 0));
      setMinimum(String(t?.minimumValidation ?? 36000));
    });
  }, [paysVise]);

  async function enregistrer(e: FormEvent) {
    e.preventDefault();
    setEnregistre(false);
    const ok = await action.lancer(() =>
      setDoc(doc(db, "tarifsPays", paysVise), {
        fraisLivraison: Number(frais),
        commissionLivreur: Number(commission),
        minimumValidation: Number(minimum),
      }),
    );
    setEnregistre(ok);
  }

  function ajouterCentre(e: FormEvent) {
    e.preventDefault();
    action.lancer(async () => {
      await addDoc(collection(db, "centres"), { nom: centre, actif: true });
      setCentre("");
    });
  }

  return (
    <>
      <h1>Paramètres</h1>
      <form className="carte etroit" onSubmit={enregistrer}>
        <h2>Tarifs de livraison · {paysVise}</h2>
        {superAdmin && (
          <label>
            Pays
            <select value={paysTarifs} onChange={(e) => setPaysTarifs(e.target.value)}>
              {PAYS.map((p) => <option key={p}>{p}</option>)}
            </select>
          </label>
        )}
        <label>Frais de livraison (FCFA)<input type="number" min={0} value={frais} onChange={(e) => setFrais(e.target.value)} required /></label>
        <label>Commission par livraison (FCFA)<input type="number" min={0} value={commission} onChange={(e) => setCommission(e.target.value)} required /></label>
        <label>Achat minimum pour la validation au siège (FCFA)<input type="number" min={0} value={minimum} onChange={(e) => setMinimum(e.target.value)} required /></label>
        {action.erreur && <p className="erreur">{action.erreur}</p>}
        {enregistre && <p className="succes">Tarifs enregistrés.</p>}
        <button className="principal" disabled={action.enCours}>Enregistrer</button>
      </form>

      <PanneauMaintenance />

      <ZonesLivraison />

      <VillesExpedition />

      <section className="carte etroit">
        <h2>Centres</h2>
        <ul className="liste">
          {centres?.map((c) => (
            <li key={c.id}>
              {c.nom}
              <button className="lien" onClick={() => action.lancer(() => deleteDoc(doc(db, "centres", c.id)))}>Retirer</button>
            </li>
          ))}
        </ul>
        <form className="actions" onSubmit={ajouterCentre}>
          <input placeholder="Nom du centre" value={centre} onChange={(e) => setCentre(e.target.value)} required />
          <button className="secondaire" disabled={action.enCours}>Ajouter</button>
        </form>
      </section>
    </>
  );
}
