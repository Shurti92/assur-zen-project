import { Component, computed, inject, signal } from '@angular/core';

import { ServerLogsService } from '../../core/server-logs/server-logs.service';
import { LogTableComponent } from '../../shared/log-table/log-table.component';
import { LogTableEntry } from '../../shared/log-table/log-table.model';
import { parseLogLines } from './parse-log-lines';

const LINES_TO_FETCH = 300;

@Component({
  selector: 'app-logs-serveur',
  templateUrl: './logs-serveur.component.html',
  imports: [LogTableComponent],
})
export class LogsServeurComponent {
  private readonly serverLogsService = inject(ServerLogsService);

  readonly entries = signal<LogTableEntry[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly sourceFile = signal<string | null>(null);

  readonly footerNote = computed(() => (this.sourceFile() ? `Fichier : ${this.sourceFile()}` : null));

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.error.set(null);
    this.serverLogsService.fetch(LINES_TO_FETCH).subscribe({
      next: (response) => {
        this.entries.set(parseLogLines(response.content));
        this.sourceFile.set(response.file);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les logs serveur.');
        this.loading.set(false);
      },
    });
  }
}
