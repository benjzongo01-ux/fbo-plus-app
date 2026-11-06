import { doc, setDoc, updateDoc } from "firebase/firestore";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { useState, type FormEvent } from "react";
import { Etat, fcfa, useAction, useCollection, type Doc } from "../commun";
import { db, stockage } from "../firebase";

const VIDE = { reference: "", nom: "", categorie: "", prix: 0, cc: 0, description: "" };

export default function Produits() {
  const { docs, erreur } = useCollection("products");
  const [recherche, setRecherche] = useState("");
  const [edition, setEdition] = useState<Partial<Doc> | null>(null);

  const visibles = docs
    ?.filter((p) => `${p.nom} ${p.reference}`.toLowerCase().includes(recherche.toLowerCase()))
    .sort((a, b) => String(a.nom).localeCompare(String(b.nom))) ?? null;

  return (
    <>
      <h1>Produits</h1>
      <div className="filtres">
        <input placeholder="Rechercher" value={recherche} onChange={(e) => setRecherche(e.target.value)} />
        <button className="principal" onClick={() => setEdition(VIDE)}>Nouveau produit</button>
      </div>
      <div className="deux-colonnes">
        <div>
          <Etat docs={visibles} erreur={erreur} vide="Aucun produit." />
          {visibles && visibles.length > 0 && (
            <table>
              <thead><tr><th>Réf.</th><th>Nom</th><th>Catégorie</th><th className="droite">Prix</th><th className="droite">CC</th><th>État</th></tr></thead>
              <tbody>
                {visibles.map((p) => (
                  <tr key={p.id} onClick={() => setEdition(p)} className={p.id === edition?.id ? "selection" : ""}>
                    <td>{p.reference}</td>
                    <td>{p.nom}</td>
                    <td>{p.categorie}</td>
                    <td className="droite">{fcfa(p.prix)}</td>
                    <td className="droite">{p.cc}</td>
                    <td>{p.actif ? "Actif" : "Inactif"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        {edition && <FormulaireProduit key={edition.id ?? "nouveau"} produit={edition} onFermer={() => setEdition(null)} />}
      </div>
    </>
  );
}

function FormulaireProduit({ produit, onFermer }: { produit: Partial<Doc>; onFermer: () => void }) {
  const [v, setV] = useState({ ...VIDE, ...produit });
  const [image, setImage] = useState<File | null>(null);
  const { enCours, erreur, lancer } = useAction();
  const champ = (nom: string) => (e: { target: { value: string } }) => setV({ ...v, [nom]: e.target.value });

  function enregistrer(e: FormEvent) {
    e.preventDefault();
    lancer(async () => {
      const id = produit.id ?? v.reference.trim();
      const donnees: Record<string, unknown> = {
        reference: v.reference.trim(),
        nom: v.nom.trim(),
        categorie: v.categorie.trim(),
        prix: Number(v.prix),
        cc: Number(v.cc),
        description: v.description,
      };
      if (image) {
        const fichier = ref(stockage, `products/${id}-${image.name}`);
        await uploadBytes(fichier, image, { contentType: image.type });
        donnees.image = await getDownloadURL(fichier);
      }
      if (produit.id) await updateDoc(doc(db, "products", id), donnees);
      else await setDoc(doc(db, "products", id), { ...donnees, actif: true });
      onFermer();
    });
  }

  const basculer = () => lancer(() => updateDoc(doc(db, "products", produit.id!), { actif: !produit.actif }).then(onFermer));

  return (
    <form className="carte detail" onSubmit={enregistrer}>
      <h2>{produit.id ? "Modifier le produit" : "Nouveau produit"}</h2>
      <label>Référence<input value={v.reference} onChange={champ("reference")} disabled={!!produit.id} required /></label>
      <label>Nom<input value={v.nom} onChange={champ("nom")} required /></label>
      <label>Catégorie<input value={v.categorie} onChange={champ("categorie")} required /></label>
      <label>Prix officiel (FCFA)<input type="number" min={0} value={v.prix} onChange={champ("prix")} required /></label>
      <label>CC<input type="number" min={0} step="0.001" value={v.cc} onChange={champ("cc")} required /></label>
      <label>Description<textarea rows={3} value={v.description} onChange={champ("description")} /></label>
      <label>Image<input type="file" accept="image/*" onChange={(e) => setImage(e.target.files?.[0] ?? null)} /></label>
      {erreur && <p className="erreur">{erreur}</p>}
      <div className="actions">
        <button className="principal" disabled={enCours}>Enregistrer</button>
        {produit.id && (
          <button type="button" className="secondaire" disabled={enCours} onClick={basculer}>
            {produit.actif ? "Désactiver" : "Réactiver"}
          </button>
        )}
        <button type="button" className="lien" onClick={onFermer}>Fermer</button>
      </div>
    </form>
  );
}
