import { RecaptchaVerifier, signInWithPhoneNumber, type ConfirmationResult } from "firebase/auth";
import { useRef, useState, type FormEvent } from "react";
import { useAction } from "../commun";
import { auth, surEmulateurs } from "../firebase";
import { useSession } from "../session";

export default function Connexion() {
  const { refuse } = useSession();
  const [telephone, setTelephone] = useState("+226");
  const [code, setCode] = useState("");
  const [confirmation, setConfirmation] = useState<ConfirmationResult | null>(null);
  const verificateur = useRef<RecaptchaVerifier | null>(null);
  const { enCours, erreur, lancer } = useAction();

  async function envoyer(e: FormEvent) {
    e.preventDefault();
    await lancer(async () => {
      verificateur.current ??= new RecaptchaVerifier(auth, "recaptcha", { size: "invisible" });
      setConfirmation(await signInWithPhoneNumber(auth, telephone, verificateur.current));
    });
  }

  async function verifier(e: FormEvent) {
    e.preventDefault();
    await lancer(() => confirmation!.confirm(code));
  }

  return (
    <div className="connexion">
      <form className="carte" onSubmit={confirmation ? verifier : envoyer}>
        <div className="logo">FBO+ <span>Admin</span></div>
        <p className="discret">Accès réservé au personnel FBO+.</p>

        {refuse && <p className="erreur">Ce compte n'a pas accès à FBO+ Admin.</p>}

        {confirmation ? (
          <label>
            Code reçu par SMS
            <input value={code} onChange={(e) => setCode(e.target.value)} inputMode="numeric" autoFocus required />
          </label>
        ) : (
          <label>
            Numéro de téléphone
            <input value={telephone} onChange={(e) => setTelephone(e.target.value)} inputMode="tel" required />
          </label>
        )}

        {erreur && <p className="erreur">{erreur}</p>}
        <button className="principal" disabled={enCours}>
          {confirmation ? "Se connecter" : "Recevoir le code"}
        </button>
        {surEmulateurs && confirmation && (
          <p className="discret">Test : le code s'affiche dans l'interface des émulateurs (Auth), port 4000.</p>
        )}
        <div id="recaptcha" />
      </form>
    </div>
  );
}
