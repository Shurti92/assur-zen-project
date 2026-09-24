export type LogTableLevel = 'debug' | 'info' | 'warn' | 'error';

export interface LogTableEntry {
  timestamp: string;
  level: LogTableLevel;
  source: string;
  message: string;
  details?: string;
}
