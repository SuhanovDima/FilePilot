'use strict';
function updateSelection() {
  document.querySelectorAll('[data-execution-form]').forEach(form => {
    const count = form.querySelectorAll('input[name="actionIds"]:checked:not(:disabled)').length;
    const label = form.querySelector('[data-selection-count]');
    const button = form.querySelector('[data-execute]');
    const confirmed = form.querySelector('input[name="confirmed"]:checked');
    if (label) label.textContent = `Выбрано операций: ${count}`;
    if (button) button.disabled = count === 0 || !confirmed;
  });
}
document.addEventListener('change', updateSelection);
document.addEventListener('DOMContentLoaded', updateSelection);
document.addEventListener('htmx:afterSwap', updateSelection);
