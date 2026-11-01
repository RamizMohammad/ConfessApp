import { useEffect } from "react";
import { navigate } from "../lib/router.ts";
import { useSession } from "../lib/session.tsx";
import { ringOf, refreshTray, useTray } from "../lib/stories.ts";
import { Aura } from "./Aura.tsx";

/** An avatar wearing its story ring: bright = something new, soft = all seen. */
export function RingAvatar({ alias, avatar, size, label }: { alias: string; avatar: string; size: number; label?: string }) {
  const tray = useTray();
  const ring = ringOf(alias, tray);
  return (
    <span className={`ring ring-${ring}`}>
      <Aura value={avatar} size={size} label={label} />
    </span>
  );
}

/** True when tapping this person's avatar should play their stories. */
export function hasStory(alias: string): boolean {
  return ringOf(alias) !== "none";
}

/** The row of story bubbles at the top of the feed: me first (add / mine), then everyone with a live story. */
export function StoryTray({ active }: { active: boolean }) {
  const { me } = useSession();
  const tray = useTray();

  useEffect(() => {
    if (active) void refreshTray();
  }, [active]);

  if (!me) return null;
  const mine = tray.find((g) => g.isMe);
  const others = tray.filter((g) => !g.isMe);

  return (
    <div className="story-tray scroll-x" role="list" aria-label="Stories">
      <div className="story-bubble" role="listitem">
        <button className="story-open" aria-label={mine ? "Your story" : "Add a story"}
                onClick={() => navigate(mine ? { name: "stories", alias: me.alias } : { name: "storyNew" })}>
          <span className={`ring ring-${mine ? "seen" : "none"}`}><Aura value={me.avatar} size={60} /></span>
          <span className="story-name">{mine ? "Your story" : "Add a story"}</span>
        </button>
        <button className="story-add" aria-label="Add a story" onClick={() => navigate({ name: "storyNew" })}>+</button>
      </div>
      {others.map((g) => (
        <div className="story-bubble" role="listitem" key={g.author.alias}>
          <button className="story-open" aria-label={`@${g.author.alias} — ${g.hasUnseen ? "new story" : "story, seen"}`}
                  onClick={() => navigate({ name: "stories", alias: g.author.alias })}>
            <span className={`ring ring-${g.hasUnseen ? "unseen" : "seen"}`}><Aura value={g.author.avatar} size={60} /></span>
            <span className="story-name">{g.author.alias}</span>
          </button>
        </div>
      ))}
    </div>
  );
}
