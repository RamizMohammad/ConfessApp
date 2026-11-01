import { useState } from "react";
import { errorMessage } from "../lib/session.tsx";
import { navigate } from "../lib/router.ts";
import * as U from "../lib/users.ts";
import { Aura } from "./Aura.tsx";
import { Confirm } from "./post.tsx";
import { useToast } from "./toast.tsx";
import { Button } from "./ui.tsx";

/** Everyone in the app who follows / unfollows: one place, so counts move together. */
type FollowListener = (alias: string, following: boolean, followers: number) => void;
const followListeners = new Set<FollowListener>();
export function onFollowChange(l: FollowListener): () => void {
  followListeners.add(l);
  return () => followListeners.delete(l);
}

export function FollowButton({ alias, following, small, onChange }:
  { alias: string; following: boolean; small?: boolean; onChange?: (following: boolean, followers: number) => void }) {
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const toast = useToast();

  async function run(next: boolean) {
    setBusy(true);
    try {
      const r = next ? await U.follow(alias) : await U.unfollow(alias);
      onChange?.(r.following, r.followers);
      followListeners.forEach((l) => l(alias, r.following, r.followers));
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Button variant={following ? "outline" : "primary"} busy={busy} className={small ? "btn-small" : ""}
              aria-pressed={following} onClick={() => (following ? setConfirm(true) : void run(true))}>
        {following ? "Following" : "Follow"}
      </Button>
      {confirm ? (
        <Confirm title={`Unfollow @${alias}?`} body="Their new letters won't appear in your Following feed." confirm="Unfollow"
                 onClose={() => setConfirm(false)} onConfirm={async () => { setConfirm(false); await run(false); }} />
      ) : null}
    </>
  );
}

export function PersonRow({ person, showFollow = true, right }:
  { person: U.Person; showFollow?: boolean; right?: React.ReactNode }) {
  const [following, setFollowing] = useState(!!person.followedByMe);
  return (
    <div className="person-row">
      <button className="person-open" onClick={() => navigate({ name: "profile", alias: person.alias })}
              aria-label={`Open @${person.alias}'s profile`}>
        <Aura value={person.avatar} size={44} />
        <span className="stack-xs grow">
          <span className="alias">@{person.alias}</span>
          {person.about ? <span className="small clamp-1">{person.about}</span> : null}
        </span>
      </button>
      {right ?? (showFollow && person.followedByMe !== undefined
        ? <FollowButton alias={person.alias} following={following} small onChange={(f) => setFollowing(f)} />
        : null)}
    </div>
  );
}
