/**
 * Screens stay mounted while others are pushed on top (Home's back stack). Android activities
 * refresh in onResume; this is that: `useOnResume(load)` runs `load` each time the screen comes
 * back to the top (not on the first show — the screen loads itself then).
 */
import { createContext, useContext, useEffect, useRef } from "react";

export const ScreenShown = createContext(true);

export function useScreenShown(): boolean {
  return useContext(ScreenShown);
}

export function useOnResume(onResume: () => void): void {
  const shown = useContext(ScreenShown);
  const was = useRef(shown);
  const cb = useRef(onResume);
  cb.current = onResume;
  useEffect(() => {
    if (shown && !was.current) cb.current();
    was.current = shown;
  }, [shown]);
}
