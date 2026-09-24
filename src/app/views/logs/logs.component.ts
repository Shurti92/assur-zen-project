import { Component, computed, inject } from '@angular/core';

import { LogService } from '../../core/logging/log.service';
import { LogTableComponent } from '../../shared/log-table/log-table.component';
import { LogTableEntry } from '../../shared/log-table/log-table.model';

@Component({
  selector: 'app-logs',
  templateUrl: './logs.component.html',
  imports: [LogTableComponent],
})
export class LogsComponent {
  private readonly logService = inject(LogService);

  readonly entries = computed<LogTableEntry[]>(() =>
    this.logService.entries().map((entry) => ({
      timestamp: entry.timestamp,
      level: entry.level,
      source: entry.category,
      message: entry.message,
      details: entry.details,
    }))
  );

  onCleared(): void {
    this.logService.clear();
  }
}
