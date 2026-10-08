import pairs from '../app/src/main/assets/ui-translations.json';
const zh = Object.fromEntries(pairs.map(([cn, jp]) => [jp, cn]));
const ja = Object.fromEntries(pairs);
const escape = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const patterns = { zh: new RegExp(Object.keys(zh).sort((a, b) => b.length - a.length).map(escape).join('|'), 'g'), ja: new RegExp(Object.keys(ja).sort((a, b) => b.length - a.length).map(escape).join('|'), 'g') };
export function translate(text, language) {
  const value = String(text || ''), dictionary = language === 'ja' ? ja : zh;
  return dictionary[value] || value.replace(patterns[language === 'ja' ? 'ja' : 'zh'], match => dictionary[match]);
}
