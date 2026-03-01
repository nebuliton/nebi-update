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
  locale: string;
  fallbackLocale: string;
  i18nEnabled: boolean;
  auditEnabled: boolean;
  analyticsEnabled: boolean;
  analyticsWeeks: number;
  exportImportEnabled: boolean;
  backupEnabled: boolean;
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

export interface AuditLogEntry {
  id: number;
  createdAt: string;
  actor: string;
  source: string;
  action: string;
  entityType: string;
  entityId: string;
  details: string;
}

export interface WeeklyAnalyticsItem {
  weekStart: string;
  added: number;
  changed: number;
  removed: number;
  total: number;
}

export interface AnalyticsPayload {
  enabled: boolean;
  windowWeeks: number;
  weeks: WeeklyAnalyticsItem[];
  totals: {
    added: number;
    changed: number;
    removed: number;
    total: number;
  };
}

export interface BackupItem {
  fileName: string;
  sizeBytes: number;
  lastModifiedAt: string;
}
