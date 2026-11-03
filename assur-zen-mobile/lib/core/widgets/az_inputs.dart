import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme/az_colors.dart';
import '../theme/az_spacing.dart';
import '../theme/az_typography.dart';
import '../utils/formatters.dart';

InputDecoration _decoration({String? hint, bool hasError = false, Widget? suffix, EdgeInsets? padding}) {
  OutlineInputBorder border(Color c, [double w = 1.5]) => OutlineInputBorder(
        borderRadius: BorderRadius.circular(AzRadius.field),
        borderSide: BorderSide(color: c, width: w),
      );
  final base = hasError ? AzColors.errorStrong : AzColors.fieldBorder;
  return InputDecoration(
    hintText: hint,
    hintStyle: AzText.body.copyWith(color: AzColors.muted.withAlpha(160)),
    isDense: false,
    filled: true,
    fillColor: Colors.white,
    counterText: '',
    contentPadding: padding ?? const EdgeInsets.symmetric(horizontal: 14, vertical: 15),
    suffixIcon: suffix,
    enabledBorder: border(base),
    focusedBorder: border(hasError ? AzColors.errorStrong : AzColors.blue700, 2),
    disabledBorder: border(AzColors.line),
    border: border(base),
  );
}

/// Libellé + champ + message d’erreur sous le champ.
class AzTextField extends StatelessWidget {
  const AzTextField({
    super.key,
    required this.label,
    this.controller,
    this.focusNode,
    this.hint,
    this.error,
    this.keyboardType,
    this.inputFormatters,
    this.maxLength,
    this.maxLines = 1,
    this.obscure = false,
    this.capitalization = TextCapitalization.none,
    this.onChanged,
    this.suffix,
    this.textInputAction,
    this.autofillHints,
    this.helper,
    this.style,
    this.textAlign = TextAlign.start,
  });

  final String label;
  final TextEditingController? controller;
  final FocusNode? focusNode;
  final String? hint;
  final String? error;
  final TextInputType? keyboardType;
  final List<TextInputFormatter>? inputFormatters;
  final int? maxLength;
  final int maxLines;
  final bool obscure;
  final TextCapitalization capitalization;
  final ValueChanged<String>? onChanged;
  final Widget? suffix;
  final TextInputAction? textInputAction;
  final Iterable<String>? autofillHints;
  final String? helper;
  final TextStyle? style;
  final TextAlign textAlign;

  @override
  Widget build(BuildContext context) {
    return _FieldFrame(
      label: label,
      error: error,
      helper: helper,
      child: TextField(
        controller: controller,
        focusNode: focusNode,
        keyboardType: keyboardType,
        inputFormatters: inputFormatters,
        maxLength: maxLength,
        maxLines: obscure ? 1 : maxLines,
        minLines: maxLines > 1 ? maxLines : null,
        obscureText: obscure,
        textCapitalization: capitalization,
        textInputAction: textInputAction,
        autofillHints: autofillHints,
        onChanged: onChanged,
        textAlign: textAlign,
        style: style ?? AzText.body,
        cursorColor: AzColors.blue700,
        decoration: _decoration(hint: hint, hasError: error != null, suffix: suffix),
      ),
    );
  }
}

class _FieldFrame extends StatelessWidget {
  const _FieldFrame({required this.label, required this.child, this.error, this.helper});

  final String label;
  final Widget child;
  final String? error;
  final String? helper;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(label, style: AzText.label),
        const SizedBox(height: 6),
        child,
        if (error != null) ...[
          const SizedBox(height: 6),
          Text(error!, style: AzText.label.copyWith(color: AzColors.error)),
        ] else if (helper != null) ...[
          const SizedBox(height: 6),
          Text(helper!, style: AzText.caption.copyWith(fontWeight: FontWeight.w500)),
        ],
      ],
    );
  }
}

/// Formate la saisie en « 07 08 12 34 56 ».
class IvorianPhoneFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    final text = formatPhone(newValue.text);
    return TextEditingValue(text: text, selection: TextSelection.collapsed(offset: text.length));
  }
}

/// Champ téléphone avec indicatif +225 fixe.
class AzPhoneField extends StatelessWidget {
  const AzPhoneField({super.key, required this.controller, this.label = 'Numéro de téléphone', this.error, this.onChanged});

  final TextEditingController controller;
  final String label;
  final String? error;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    return _FieldFrame(
      label: label,
      error: error,
      child: Row(
        children: [
          Container(
            height: 54,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: AzColors.background,
              borderRadius: BorderRadius.circular(AzRadius.field),
              border: Border.all(color: AzColors.fieldBorder, width: 1.5),
            ),
            child: Text('+225', style: AzText.body.copyWith(fontWeight: FontWeight.w700)),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: TextField(
              controller: controller,
              keyboardType: TextInputType.phone,
              autofillHints: const [AutofillHints.telephoneNumberNational],
              inputFormatters: [IvorianPhoneFormatter()],
              onChanged: onChanged,
              style: AzText.body.copyWith(letterSpacing: 0.5),
              cursorColor: AzColors.blue700,
              decoration: _decoration(hint: '07 00 00 00 00', hasError: error != null),
            ),
          ),
        ],
      ),
    );
  }
}

/// Champ de code à 6 chiffres (PIN ou code SMS).
class AzCodeField extends StatefulWidget {
  const AzCodeField({
    super.key,
    required this.controller,
    required this.label,
    this.error,
    this.obscure = true,
    this.autofocus = false,
    this.onCompleted,
    this.onChanged,
  });

  final TextEditingController controller;
  final String label;
  final String? error;
  final bool obscure;
  final bool autofocus;
  final ValueChanged<String>? onCompleted;
  final ValueChanged<String>? onChanged;

  @override
  State<AzCodeField> createState() => _AzCodeFieldState();
}

class _AzCodeFieldState extends State<AzCodeField> {
  bool _visible = false;

  @override
  Widget build(BuildContext context) {
    final hide = widget.obscure && !_visible;
    return _FieldFrame(
      label: widget.label,
      error: widget.error,
      child: TextField(
        controller: widget.controller,
        autofocus: widget.autofocus,
        keyboardType: TextInputType.number,
        obscureText: hide,
        obscuringCharacter: '•',
        maxLength: 6,
        textAlign: TextAlign.center,
        autofillHints: widget.obscure ? null : const [AutofillHints.oneTimeCode],
        inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
        style: AzText.headline.copyWith(letterSpacing: 12, fontWeight: FontWeight.w800),
        cursorColor: AzColors.blue700,
        onChanged: (v) {
          widget.onChanged?.call(v);
          if (v.length == 6) widget.onCompleted?.call(v);
        },
        decoration: _decoration(
          hint: '••••••',
          hasError: widget.error != null,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
          suffix: widget.obscure
              ? IconButton(
                  tooltip: _visible ? 'Masquer le code' : 'Afficher le code',
                  onPressed: () => setState(() => _visible = !_visible),
                  icon: Icon(_visible ? Icons.visibility_off_outlined : Icons.visibility_outlined, color: AzColors.muted),
                )
              : null,
        ),
      ),
    );
  }
}

/// Champ date en lecture seule qui ouvre le sélecteur de date (en français).
class AzDateField extends StatelessWidget {
  const AzDateField({
    super.key,
    required this.label,
    required this.value,
    required this.onChanged,
    required this.firstDate,
    required this.lastDate,
    this.error,
  });

  final String label;
  final DateTime? value;
  final ValueChanged<DateTime> onChanged;
  final DateTime firstDate;
  final DateTime lastDate;
  final String? error;

  @override
  Widget build(BuildContext context) {
    return _FieldFrame(
      label: label,
      error: error,
      child: _PickerBox(
        text: value == null ? 'Choisir une date' : formatDate(value),
        icon: Icons.calendar_today_outlined,
        placeholder: value == null,
        hasError: error != null,
        onTap: () async {
          final picked = await showDatePicker(
            context: context,
            initialDate: value ?? DateTime.now(),
            firstDate: firstDate,
            lastDate: lastDate,
            locale: const Locale('fr'),
            helpText: label,
            cancelText: 'Annuler',
            confirmText: 'Valider',
          );
          if (picked != null) onChanged(picked);
        },
      ),
    );
  }
}

/// Champ heure en lecture seule.
class AzTimeField extends StatelessWidget {
  const AzTimeField({super.key, required this.label, required this.value, required this.onChanged});

  final String label;
  final TimeOfDay value;
  final ValueChanged<TimeOfDay> onChanged;

  @override
  Widget build(BuildContext context) {
    final text = '${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';
    return _FieldFrame(
      label: label,
      child: _PickerBox(
        text: text,
        icon: Icons.schedule_outlined,
        onTap: () async {
          final picked = await showTimePicker(
            context: context,
            initialTime: value,
            helpText: label,
            cancelText: 'Annuler',
            confirmText: 'Valider',
          );
          if (picked != null) onChanged(picked);
        },
      ),
    );
  }
}

class _PickerBox extends StatelessWidget {
  const _PickerBox({required this.text, required this.icon, required this.onTap, this.placeholder = false, this.hasError = false});

  final String text;
  final IconData icon;
  final VoidCallback onTap;
  final bool placeholder;
  final bool hasError;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AzRadius.field),
        side: BorderSide(color: hasError ? AzColors.errorStrong : AzColors.fieldBorder, width: 1.5),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: SizedBox(
          height: 52,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: [
                Expanded(
                  child: Text(text,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: AzText.body.copyWith(color: placeholder ? AzColors.muted : AzColors.ink)),
                ),
                Icon(icon, size: 20, color: AzColors.muted),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Champ avec auto-complétion : propose les valeurs de [options] qui correspondent à la
/// saisie (sans tenir compte des majuscules ni des accents). La saisie libre reste possible.
/// Sans [options], c'est un simple champ texte.
class AzAutocompleteField extends StatefulWidget {
  const AzAutocompleteField({
    super.key,
    required this.label,
    required this.controller,
    required this.options,
    this.hint,
    this.error,
    this.onChanged,
    this.capitalization = TextCapitalization.words,
  });

  final String label;
  final TextEditingController controller;
  final List<String> options;
  final String? hint;
  final String? error;
  final ValueChanged<String>? onChanged;
  final TextCapitalization capitalization;

  @override
  State<AzAutocompleteField> createState() => _AzAutocompleteFieldState();
}

class _AzAutocompleteFieldState extends State<AzAutocompleteField> {
  final _focus = FocusNode();

  @override
  void dispose() {
    _focus.dispose();
    super.dispose();
  }

  static String _norm(String s) {
    const from = 'àâäáãçéèêëíîïìñóôöòõúùûüýÿ';
    const to = 'aaaaaceeeeiiiinooooouuuuyy';
    final b = StringBuffer();
    for (final c in s.toLowerCase().trim().split('')) {
      final i = from.indexOf(c);
      b.write(i >= 0 ? to[i] : c);
    }
    return b.toString();
  }

  /// Les valeurs qui commencent par la saisie d'abord, puis celles qui la contiennent.
  Iterable<String> _suggest(TextEditingValue v) {
    final q = _norm(v.text);
    if (q.isEmpty) return widget.options;
    final starts = <String>[];
    final contains = <String>[];
    for (final o in widget.options) {
      final n = _norm(o);
      if (n == q) continue; // déjà saisi en entier
      if (n.startsWith(q)) {
        starts.add(o);
      } else if (n.contains(q)) {
        contains.add(o);
      }
    }
    return [...starts, ...contains];
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) => RawAutocomplete<String>(
        textEditingController: widget.controller,
        focusNode: _focus,
        optionsBuilder: _suggest,
        onSelected: (v) => widget.onChanged?.call(v),
        fieldViewBuilder: (context, controller, focusNode, onSubmitted) => AzTextField(
          label: widget.label,
          controller: controller,
          focusNode: focusNode,
          hint: widget.hint,
          error: widget.error,
          capitalization: widget.capitalization,
          onChanged: widget.onChanged,
        ),
        optionsViewBuilder: (context, onSelected, options) => Align(
          alignment: Alignment.topLeft,
          child: Material(
            elevation: 4,
            color: Colors.white,
            borderRadius: BorderRadius.circular(AzRadius.field),
            child: ConstrainedBox(
              constraints: BoxConstraints(maxHeight: 220, maxWidth: constraints.maxWidth),
              child: ListView.builder(
                padding: EdgeInsets.zero,
                shrinkWrap: true,
                itemCount: options.length,
                itemBuilder: (context, i) {
                  final o = options.elementAt(i);
                  return InkWell(
                    onTap: () => onSelected(o),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
                      child: Text(o, style: AzText.body),
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Case à cocher + texte, toute la ligne est cliquable.
class AzCheckRow extends StatelessWidget {
  const AzCheckRow({super.key, required this.value, required this.onChanged, required this.child});

  final bool value;
  final ValueChanged<bool> onChanged;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: InkWell(
        onTap: () => onChanged(!value),
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: 24,
                height: 24,
                child: Checkbox(
                  value: value,
                  onChanged: (v) => onChanged(v ?? false),
                  activeColor: AzColors.blue700,
                  materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  visualDensity: VisualDensity.compact,
                  side: const BorderSide(color: AzColors.fieldBorder, width: 1.5),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(5)),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(child: DefaultTextStyle(style: AzText.bodyMedium.copyWith(color: AzColors.ink2), child: child)),
            ],
          ),
        ),
      ),
    );
  }
}
