import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

type ToastFn = (text: string, action?: { label: string; run: () => void }) => void;
const Ctx = createContext<ToastFn>(() => {});
export const useToast = () => useContext(Ctx);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [t, setT] = useState<{ text: string; action?: { label: string; run: () => void }; key: number } | null>(null);
  const timer = useRef<number | undefined>(undefined);
  const show = useCallback<ToastFn>((text, action) => {
    window.clearTimeout(timer.current);
    setT({ text, action, key: Date.now() });
    timer.current = window.setTimeout(() => setT(null), action ? 5000 : 3000);
  }, []);
  return (
    <Ctx.Provider value={show}>
      {children}
      {t ? (
        <div className="toast" role="status" key={t.key}>
          <span>{t.text}</span>
          {t.action ? (
            <button className="btn btn-primary" onClick={() => { t.action!.run(); setT(null); }}>{t.action.label}</button>
          ) : null}
        </div>
      ) : null}
    </Ctx.Provider>
  );
}
