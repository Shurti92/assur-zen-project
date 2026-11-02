import { LogTableEntry, LogTableLevel } from '../../shared/log-table/log-table.model';

// Matches Logback's default pattern: "yyyy-MM-dd HH:mm:ss,SSS LEVEL logger [thread] message"
const LOG_LINE_PATTERN =
  /^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3})\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+(\S+)\s+\[[^\]]*\]\s?(.*)$/;

const LEVEL_MAP: Record<string, LogTableLevel> = {
  TRACE: 'debug',
  DEBUG: 'debug',
  INFO: 'info',
  WARN: 'warn',
  ERROR: 'error',
};

function toIsoTimestamp(raw: string): string {
  // "2026-07-31 06:52:12,549" -> "2026-07-31T06:52:12.549"
  return raw.replace(' ', 'T').replace(',', '.');
}

/**
 * Server log files interleave single-line records with multi-line ones (stack traces,
 * startup banners, indented detail blocks). Lines that don't start a new timestamped
 * record are folded into the `details` of the record above them, so each table row
 * still corresponds to one real log event rather than one raw text line.
 */
export function parseLogLines(lines: string[]): LogTableEntry[] {
  const entries: LogTableEntry[] = [];

  for (const rawLine of lines) {
    const match = LOG_LINE_PATTERN.exec(rawLine);
    if (match) {
      const [, timestamp, level, logger, message] = match;
      entries.push({
        timestamp: toIsoTimestamp(timestamp),
        level: LEVEL_MAP[level] ?? 'info',
        source: logger,
        message,
      });
      continue;
    }

    if (rawLine.trim() === '') {
      continue;
    }

    const previous = entries[entries.length - 1];
    if (previous) {
      previous.details = previous.details ? `${previous.details}\n${rawLine}` : rawLine;
    } else {
      // Content starts mid-record (e.g. the requested window cuts into a multi-line entry).
      entries.push({ timestamp: new Date().toISOString(), level: 'info', source: '—', message: rawLine });
    }
  }

  return entries;
}
