/** Lucide-style icons, 1.5 stroke — the same set the Android app uses. */
import type { SVGProps } from "react";

function I({ children, ...p }: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"
         aria-hidden width="22" height="22" {...p}>{children}</svg>
  );
}

export const Heart = ({ filled, ...p }: SVGProps<SVGSVGElement> & { filled?: boolean }) => (
  <I {...p}><path fill={filled ? "currentColor" : "none"} d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" /></I>
);
export const Message = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z" /></I>;
export const MessageOff = (p: SVGProps<SVGSVGElement>) => (
  <I {...p}><path d="M20.5 14.9A9 9 0 0 0 9.1 3.5" /><path d="m2 2 20 20" /><path d="M5.6 5.6C3 8.3 2.2 12.5 4 16l-2 6 6-2c3.4 1.8 7.6 1.1 10.3-1.7" /></I>
);
export const More = (p: SVGProps<SVGSVGElement>) => <I {...p}><circle cx="5" cy="12" r="1" /><circle cx="12" cy="12" r="1" /><circle cx="19" cy="12" r="1" /></I>;
export const Flag = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" /><path d="M4 22v-7" /></I>;
export const Send = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z" /><path d="m21.854 2.147-10.94 10.939" /></I>;
export const Feather = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M12.67 19a2 2 0 0 0 1.416-.588l6.154-6.172a6 6 0 0 0-8.49-8.49L5.586 9.914A2 2 0 0 0 5 11.328V18a1 1 0 0 0 1 1z" /><path d="M16 8 2 22" /><path d="M17.5 15H9" /></I>;
export const GifIcon = (p: SVGProps<SVGSVGElement>) => (
  <I {...p}><rect x="2" y="5" width="20" height="14" rx="2" /><path d="M10.2 9.6H8a1.5 1.5 0 0 0-1.5 1.5v1.8A1.5 1.5 0 0 0 8 14.4h.7a1.5 1.5 0 0 0 1.5-1.5v-.8H8.8" /><path d="M12.6 9.6v4.8" /><path d="M15.2 14.4V9.6h3M15.2 12h2.4" /></I>
);
export const Close = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M18 6 6 18" /><path d="m6 6 12 12" /></I>;
export const Trash = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" /></I>;
export const Ban = (p: SVGProps<SVGSVGElement>) => <I {...p}><circle cx="12" cy="12" r="9" /><path d="m5.6 5.6 12.8 12.8" /></I>;
export const UserX = (p: SVGProps<SVGSVGElement>) => <I {...p}><circle cx="9" cy="8" r="4" /><path d="M2 21a7 7 0 0 1 14 0" /><path d="m17 8 4 4m0-4-4 4" /></I>;
export const Eye = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></I>;
export const Pencil = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" /></I>;
export const Inbox = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M22 12h-6l-2 3h-4l-2-3H2" /><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" /></I>;
export const Refresh = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M3 12a9 9 0 0 1 15-6.7L21 8M21 3v5h-5M21 12a9 9 0 0 1-15 6.7L3 16M3 21v-5h5" /></I>;
export const Bell = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" /></I>;
export const Home = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V21h14V9.5" /></I>;
export const User = (p: SVGProps<SVGSVGElement>) => <I {...p}><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></I>;
export const Users = (p: SVGProps<SVGSVGElement>) => <I {...p}><circle cx="9" cy="8" r="4" /><path d="M2 21a7 7 0 0 1 14 0" /><path d="M16 3.1a4 4 0 0 1 0 7.8M22 21a7 7 0 0 0-4-6.3" /></I>;
export const Settings = (p: SVGProps<SVGSVGElement>) => (
  <I {...p}><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" /><circle cx="12" cy="12" r="3" /></I>
);
export const Search = (p: SVGProps<SVGSVGElement>) => <I {...p}><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></I>;
export const Flame = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.07-2.14-.22-4.05 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.15.43-2.29 1-3a2.5 2.5 0 0 0 2.5 2.5z" /></I>;
export const Letters = (p: SVGProps<SVGSVGElement>) => <I {...p}><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" /></I>;
export const Lock = (p: SVGProps<SVGSVGElement>) => <I {...p}><rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></I>;
export const Phone = (p: SVGProps<SVGSVGElement>) => <I {...p}><rect x="6" y="2" width="12" height="20" rx="2" /><path d="M11 18h2" /></I>;
export const Shuffle = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M2 18h1.4c1.3 0 2.5-.6 3.3-1.7l6.1-8.6c.7-1.1 2-1.7 3.3-1.7H22" /><path d="m18 2 4 4-4 4" /><path d="M2 6h1.9c1.5 0 2.9.9 3.6 2.2" /><path d="M22 18h-5.9c-1.3 0-2.6-.7-3.3-1.8l-.5-.8" /><path d="m18 14 4 4-4 4" /></I>;
export const Lightbulb = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M9 18h6M10 22h4" /><path d="M12 2a7 7 0 0 0-4 12.7V17h8v-2.3A7 7 0 0 0 12 2Z" /></I>;
export const BookIcon = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20" /></I>;
export const Plus = (p: SVGProps<SVGSVGElement>) => <I {...p}><path d="M12 5v14M5 12h14" /></I>;
