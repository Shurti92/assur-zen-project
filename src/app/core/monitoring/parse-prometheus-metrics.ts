import { PrometheusMetricFamily } from './models/monitoring.model';

const LABEL_PATTERN = /([a-zA-Z_][a-zA-Z0-9_]*)="((?:[^"\\]|\\.)*)"/g;
const SAMPLE_PATTERN = /^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{(.*)\})?\s+(\S+)/;

function unescapeLabelValue(value: string): string {
  return value.replace(/\\n/g, '\n').replace(/\\"/g, '"').replace(/\\\\/g, '\\');
}

function familyFor(families: Map<string, PrometheusMetricFamily>, name: string): PrometheusMetricFamily {
  let family = families.get(name);
  if (!family) {
    family = { name, help: '', type: 'unknown', samples: [] };
    families.set(name, family);
  }
  return family;
}

/** Parses the OpenMetrics/Prometheus text exposition format returned by
 * `/actuator/prometheus` (one HELP/TYPE header per metric family, followed by
 * `metric{label="value",...} number` sample lines) into structured families. */
export function parsePrometheusMetrics(raw: string): PrometheusMetricFamily[] {
  const families = new Map<string, PrometheusMetricFamily>();

  for (const line of raw.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed) {
      continue;
    }

    if (trimmed.startsWith('# HELP ')) {
      const rest = trimmed.slice(7);
      const spaceIndex = rest.indexOf(' ');
      const name = spaceIndex === -1 ? rest : rest.slice(0, spaceIndex);
      familyFor(families, name).help = spaceIndex === -1 ? '' : rest.slice(spaceIndex + 1);
      continue;
    }

    if (trimmed.startsWith('# TYPE ')) {
      const rest = trimmed.slice(7);
      const spaceIndex = rest.indexOf(' ');
      if (spaceIndex === -1) {
        continue;
      }
      const name = rest.slice(0, spaceIndex);
      familyFor(families, name).type = rest.slice(spaceIndex + 1).trim();
      continue;
    }

    if (trimmed.startsWith('#')) {
      continue;
    }

    const match = SAMPLE_PATTERN.exec(trimmed);
    if (!match) {
      continue;
    }
    const [, metric, , labelsRaw, valueRaw] = match;
    const value = Number(valueRaw);
    if (Number.isNaN(value)) {
      continue;
    }

    const labels: Record<string, string> = {};
    if (labelsRaw) {
      LABEL_PATTERN.lastIndex = 0;
      let labelMatch: RegExpExecArray | null;
      while ((labelMatch = LABEL_PATTERN.exec(labelsRaw))) {
        labels[labelMatch[1]] = unescapeLabelValue(labelMatch[2]);
      }
    }

    familyFor(families, metric).samples.push({ metric, labels, value });
  }

  return Array.from(families.values());
}
