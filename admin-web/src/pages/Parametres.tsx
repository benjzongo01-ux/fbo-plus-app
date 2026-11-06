import { addDoc, collection, deleteDoc, doc, onSnapshot, setDoc } from "firebase/firestore";
import { useEffect, useState, type FormEvent } from "react";
import { useAction, useCollection } from "../commun";
import { db } from "../firebase";

export default function Parametres() {
  const [frais, setFrais] = useState("");
  const [commission, setCommission] = useState("");
  const [enregistre, setEnregistre] = useState(false);
  const action = useAction();
  const { docs: centres } = useCollection("centres");
  const [centre, setCentre] = useState("");

  useEffect(
    () =>
      onSnapshot(doc(db, "parametres", "tarifs"), (d) => {
        setFrais(String(d.data()?.fraisLivraison ?? 0));
        setCommission(String(d.data()?.commissionLivreur ?? 0));
      }),
    [],
  );

  async function enregistrer(e: FormEvent) {
    e.preventDefault();
    setEnregistre(false);
    const ok = await action.lancer(() =>
      setDoc(doc(db, "parametres", "tarifs"), {
        fraisLivraison: Number(frais),
        commissionLivreur: Number(commission),
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
        <h2>Tarifs</h2>
        <label>Frais de livraison (FCFA)<input type="number" min={0} value={frais} onChange={(e) => setFrais(e.target.value)} required /></label>
        <label>Commission par livraison (FCFA)<input type="number" min={0} value={commission} onChange={(e) => setCommission(e.target.value)} required /></label>
        {action.erreur && <p className="erreur">{action.erreur}</p>}
        {enregistre && <p className="succes">Tarifs enregistrés.</p>}
        <button className="principal" disabled={action.enCours}>Enregistrer</button>
      </form>

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
