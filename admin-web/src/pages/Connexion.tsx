import {
  createUserWithEmailAndPassword,
  GoogleAuthProvider,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPopup,
} from "firebase/auth";
import { useState, type FormEvent } from "react";
import { useAction } from "../commun";
import { auth, surEmulateurs } from "../firebase";
import { useSession } from "../session";

type Mode = "connexion" | "premiere" | "oubli";

/**
 * Accès réservé au personnel invité par le super administrateur : connexion Google (recommandée)
 * ou email et mot de passe. Une adresse non invitée est refusée par le serveur.
 */
export default function Connexion() {
  const { refus } = useSession();
  const [mode, setMode] = useState<Mode>("connexion");
  const [email, setEmail] = useState("");
  const [motDePasse, setMotDePasse] = useState("");
  const [info, setInfo] = useState("");
  const { enCours, erreur, lancer } = useAction();

  const google = () => lancer(() => signInWithPopup(auth, new GoogleAuthProvider()));

  async function valider(e: FormEvent) {
    e.preventDefault();
    setInfo("");
    if (mode === "connexion") {
      await lancer(() => signInWithEmailAndPassword(auth, email.trim(), motDePasse));
    } else if (mode === "premiere") {
      const ok = await lancer(async () => {
        const { user } = await createUserWithEmailAndPassword(auth, email.trim(), motDePasse);
        await sendEmailVerification(user);
      });
      if (ok) setInfo("Un lien de confirmation a été envoyé à votre adresse. Cliquez dessus, puis connectez-vous.");
    } else {
      const ok = await lancer(() => sendPasswordResetEmail(auth, email.trim()));
      if (ok) setInfo("Si cette adresse a un compte, un lien pour choisir un nouveau mot de passe vient d'être envoyé.");
    }
  }

  return (
    <div className="connexion">
      <form className="carte" onSubmit={valider}>
        <div className="logo">FBO+ <span>Admin</span></div>
        <p className="discret">Accès réservé au personnel invité par l'administration FBO+.</p>

        {refus && <p className="erreur">{refus}</p>}

        <button type="button" className="google" disabled={enCours} onClick={google}>
          <span aria-hidden="true">G</span> Continuer avec Google
        </button>
        <div className="separateur"><span>ou avec votre email</span></div>

        <label>
          Adresse email
          <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        {mode !== "oubli" && (
          <label>
            {mode === "premiere" ? "Choisissez un mot de passe (8 caractères au moins)" : "Mot de passe"}
            <input
              type="password"
              autoComplete={mode === "premiere" ? "new-password" : "current-password"}
              minLength={mode === "premiere" ? 8 : undefined}
              value={motDePasse}
              onChange={(e) => setMotDePasse(e.target.value)}
              required
            />
          </label>
        )}

        {erreur && <p className="erreur">{erreur}</p>}
        {info && <p className="succes">{info}</p>}
        <button className="principal" disabled={enCours}>
          {mode === "connexion" ? "Se connecter" : mode === "premiere" ? "Créer mon mot de passe" : "Recevoir le lien"}
        </button>

        <div className="liens">
          {mode !== "connexion" && <button type="button" className="lien" onClick={() => setMode("connexion")}>J'ai déjà un mot de passe</button>}
          {mode !== "premiere" && <button type="button" className="lien" onClick={() => setMode("premiere")}>Première connexion par email</button>}
          {mode !== "oubli" && <button type="button" className="lien" onClick={() => setMode("oubli")}>Mot de passe oublié</button>}
        </div>
        {surEmulateurs && (
          <p className="discret">Test : Google ouvre un compte fictif des émulateurs ; les liens d'email s'affichent dans le terminal des émulateurs.</p>
        )}
      </form>
    </div>
  );
}
