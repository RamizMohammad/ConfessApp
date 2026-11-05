import { useEffect } from "react";
import { navigate } from "../lib/router.ts";
import { useSession } from "../lib/session.tsx";
import { ringOf, refreshTray, useTray } from "../lib/stories.ts";
import { Aura } from "./Aura.tsx";
import { S } from "../lib/strings.ts";
import { Icon } from "../ui/Icon.tsx";

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

/** The row of story bubbles at the top of the feed (item_story_bubble + StoryTrayAdapter):
 *  "Your story" first (a + badge to add one), then everyone with a live story. */
export function StoryTray({ active }: { active: boolean }) {
  const { me } = useSession();
  const tray = useTray();

  useEffect(() => {
    if (active) void refreshTray();
  }, [active]);

  if (!me) return null;
  const mine = tray.find((g) => g.isMe);
  const groups = mine ? [mine, ...tray.filter((g) => !g.isMe)] : tray.filter((g) => !g.isMe);

  return (
    <div className="story-tray-a scroll-x" role="list" aria-label="Stories">
      {!mine ? (
        <div className="sb" role="listitem">
          <button type="button" className="sb-open" aria-label={S("story_add")} onClick={() => navigate({ name: "storyNew" })}>
            <span className="sb-frame"><span className="sb-ring seen"><Aura value={me.avatar} size={52} /></span>
              <span className="sb-add" aria-hidden><Icon name="plus" size={14} /></span></span>
            <span className="sb-label t-meta-small">{S("story_yours")}</span>
          </button>
        </div>
      ) : null}
      {groups.map((g) => {
        const who = g.isMe ? S("story_yours") : `@${g.author.alias}`;
        return (
          <div className="sb" role="listitem" key={g.author.alias}>
            <button type="button" className="sb-open" aria-label={S(g.hasUnseen ? "story_cd_unseen" : "story_cd_seen", who)}
                    onClick={() => navigate({ name: "stories", alias: g.author.alias })}>
              <span className="sb-frame"><span className={`sb-ring ${g.hasUnseen ? "unseen" : "seen"}`}><Aura value={g.author.avatar} size={52} /></span></span>
              <span className="sb-label t-meta-small">{g.isMe ? S("story_yours") : g.author.alias}</span>
            </button>
            {g.isMe ? (
              <button type="button" className="sb-add" aria-label={S("story_add")} onClick={() => navigate({ name: "storyNew" })}>
                <Icon name="plus" size={14} />
              </button>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
