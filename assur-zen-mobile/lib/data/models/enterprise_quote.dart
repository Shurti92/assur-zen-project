/// Cotation Assurance Entreprise : formules, options, prime annuelle TTC, garanties et limites.
///
/// Tout vient du serveur (liste, noms, descriptions, montants, garanties). L'application n'additionne,
/// ne remise et ne déduit rien : la prime est un seul montant TTC.
library;

class EnterpriseOption {
  const EnterpriseOption({required this.id, required this.label, required this.total});

  /// Identifiant renvoyé tel quel à la simulation (OPTION_1, OPTION_2…) : non consécutifs possibles.
  final String id;
  final String label;

  /// Prime annuelle TTC en FCFA.
  final int total;
}

class EnterprisePack {
  const EnterprisePack({required this.id, required this.name, required this.options, this.description});

  final String id;
  final String name;
  final String? description;

  /// Une formule peut n'avoir qu'une seule option.
  final List<EnterpriseOption> options;
}

class EnterpriseForm {
  const EnterpriseForm({required this.packs, this.durationMonths});

  final List<EnterprisePack> packs;
  final int? durationMonths;
}

/// Une garantie. `level` 1 : ligne « dont » rattachée à la ligne de niveau 0 qui précède.
class EnterpriseGuarantee {
  const EnterpriseGuarantee({
    required this.name,
    this.level = 0,
    this.indemnityAmount,
    this.indemnityLabel,
    this.deductible,
    this.basis,
  });

  final String name;
  final int level;

  /// Montant d'indemnité en FCFA ; absent : `indemnityLabel` le remplace (« Selon barème », « Illimité »).
  final int? indemnityAmount;
  final String? indemnityLabel;
  final String? deductible;
  final String? basis;

  bool get hasDetail => deductible != null || basis != null;
}

/// Une garantie et ses lignes « dont ».
class EnterpriseGuaranteeGroup {
  const EnterpriseGuaranteeGroup(this.main, this.children);

  final EnterpriseGuarantee main;
  final List<EnterpriseGuarantee> children;

  bool get expandable => main.hasDetail || children.isNotEmpty;
}

class EnterpriseLimit {
  const EnterpriseLimit({required this.label, this.amount, this.text});

  final String label;
  final int? amount;
  final String? text;
}

class EnterpriseSimulation {
  const EnterpriseSimulation({
    required this.formulaId,
    required this.formulaName,
    required this.option,
    required this.optionLabel,
    required this.total,
    this.formulaDescription,
    this.durationMonths,
    this.currency,
    this.guarantees = const [],
    this.limits = const [],
    this.note,
  });

  final String formulaId;
  final String formulaName;
  final String? formulaDescription;
  final String option;
  final String optionLabel;
  final int? durationMonths;

  /// Prime annuelle TTC, sans décomposition.
  final int total;
  final String? currency;
  final List<EnterpriseGuarantee> guarantees;
  final List<EnterpriseLimit> limits;
  final String? note;

  /// Garanties regroupées : une ligne de niveau 1 se rattache à la dernière ligne de niveau 0.
  List<EnterpriseGuaranteeGroup> get guaranteeGroups {
    final groups = <EnterpriseGuaranteeGroup>[];
    EnterpriseGuarantee? current;
    var children = <EnterpriseGuarantee>[];
    void flush() {
      final c = current;
      if (c != null) groups.add(EnterpriseGuaranteeGroup(c, children));
      children = <EnterpriseGuarantee>[];
    }

    for (final g in guarantees) {
      if (g.level >= 1 && current != null) {
        children.add(g);
      } else {
        flush();
        current = g;
      }
    }
    flush();
    return groups;
  }
}
