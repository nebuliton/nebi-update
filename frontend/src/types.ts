export type MessageTone = "info" | "error";

export interface DashboardStatus {
  connected: boolean;
  weekLabel: string;
  weekStart: string;
  weekEnd: string;
  updateCount: number;
  channelId: string;
  guildId: string;
  scheduleDay: string;
  scheduleTime: string;
  timezone: string;
}

export interface DashboardUpdate {
  id: number;
  weekStart: string;
  type: string;
  content: string;
  author: string;
  createdAt: string;
  updatedAt: string;
}

export interface MessageState {
  text: string;
  tone: MessageTone;
}
