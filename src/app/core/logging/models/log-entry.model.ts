export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export type LogCategory = 'app' | 'auth' | 'http' | 'navigation' | 'error';

export interface LogEntry {
  timestamp: string;
  level: LogLevel;
  category: LogCategory;
  message: string;
  details?: string;
}
