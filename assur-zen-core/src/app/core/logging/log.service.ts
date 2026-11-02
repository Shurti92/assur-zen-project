import { Injectable, signal } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';

import { LogCategory, LogEntry, LogLevel } from './models/log-entry.model';

const STORAGE_KEY = 'app_logs';
const MAX_ENTRIES = 500;

@Injectable({ providedIn: 'root' })
export class LogService {
  private readonly entriesSignal = signal<LogEntry[]>(this.loadFromStorage());

  readonly entries = this.entriesSignal.asReadonly();

  constructor(router: Router) {
    this.log('info', 'app', 'Application démarrée');

    router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe((event) => {
      this.log('info', 'navigation', `Navigation vers ${(event as NavigationEnd).urlAfterRedirects}`);
    });
  }

  debug(category: LogCategory, message: string, details?: string): void {
    this.log('debug', category, message, details);
  }

  info(category: LogCategory, message: string, details?: string): void {
    this.log('info', category, message, details);
  }

  warn(category: LogCategory, message: string, details?: string): void {
    this.log('warn', category, message, details);
  }

  error(category: LogCategory, message: string, details?: string): void {
    this.log('error', category, message, details);
  }

  log(level: LogLevel, category: LogCategory, message: string, details?: string): void {
    const entry: LogEntry = { timestamp: new Date().toISOString(), level, category, message, details };
    const next = [...this.entriesSignal(), entry].slice(-MAX_ENTRIES);
    this.entriesSignal.set(next);
    this.persist(next);
  }

  clear(): void {
    this.entriesSignal.set([]);
    this.persist([]);
  }

  exportAsText(): string {
    return this.entriesSignal()
      .map((entry) => {
        const line = `[${entry.timestamp}] [${entry.level.toUpperCase()}] [${entry.category}] ${entry.message}`;
        return entry.details ? `${line}\n    ${entry.details}` : line;
      })
      .join('\n');
  }

  downloadAsFile(): void {
    const blob = new Blob([this.exportAsText()], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `assurzen-logs-${new Date().toISOString().replace(/[:.]/g, '-')}.log`;
    link.click();
    URL.revokeObjectURL(url);
  }

  private persist(entries: LogEntry[]): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
    } catch {
      // Storage full or unavailable: logs remain available in memory for this session.
    }
  }

  private loadFromStorage(): LogEntry[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as LogEntry[]) : [];
    } catch {
      return [];
    }
  }
}
