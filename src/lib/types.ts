/** Shapes the server sends (API_V2.md). */

export type Reaction = "felt" | "hug" | "same" | "strength" | "tears";
export type Mood = "vent" | "advice" | "listen" | "happy" | "heartbreak";

export interface Author {
  alias: string;
  avatar: string;
}

export interface Gif {
  id: string;
  width: number | null;
  height: number | null;
  url: string;
  previewUrl: string;
  stillUrl: string;
  source: "giphy";
  title?: string;
}

export interface Poll {
  options: { text: string; votes?: number }[];
  totalVotes: number;
  myVote: number | null;
  closesAt: string;
  closed: boolean;
}

export interface Post {
  postId: string;
  author: Author;
  text: string;
  commentsEnabled: boolean;
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  myReaction: Reaction | null;
  reactions: Partial<Record<Reaction, number>>;
  mood: Mood | null;
  letterTo: string | null;
  gif: Gif | null;
  poll: Poll | null;
  isMine: boolean;
  createdAt: string;
  chapter: { bookId: string; chapterNo: number; title: string | null } | null;
  riskScore: number | null;
  sensitive: boolean;
  burst: { id: string; count: number } | null;
}

export interface Comment {
  commentId: string;
  postId: string;
  author: Author;
  text: string;
  gif: Gif | null;
  isMine: boolean;
  isPostAuthor: boolean;
  createdAt: string;
}

export interface Page<K extends string, T> {
  nextCursor: string | null;
  items: T[];
  key: K;
}

export const REACTIONS: { key: Reaction; emoji: string; label: string }[] = [
  { key: "felt", emoji: "❤️", label: "Felt" },
  { key: "hug", emoji: "🫂", label: "Hug" },
  { key: "same", emoji: "🤝", label: "Same" },
  { key: "strength", emoji: "💪", label: "Strength" },
  { key: "tears", emoji: "🥺", label: "Tears" },
];

export const MOODS: { key: Mood; label: string }[] = [
  { key: "vent", label: "Vent" },
  { key: "advice", label: "Advice wanted" },
  { key: "listen", label: "Just listen" },
  { key: "happy", label: "Happy news" },
  { key: "heartbreak", label: "Heartbreak" },
];

export const REPORT_REASONS: { key: string; label: string }[] = [
  { key: "hate_or_harassment", label: "Hate or harassment" },
  { key: "reveals_identity", label: "Reveals someone's identity" },
  { key: "threat_or_self_harm", label: "Threats or self-harm risk" },
  { key: "sexual_or_minor", label: "Sexual content or involves a minor" },
  { key: "spam", label: "Spam or promotion" },
  { key: "other", label: "Something else" },
];

export const LIMITS = { post: 2000, gifPost: 100, comment: 500, letterTo: 40, pollOption: 60 } as const;
