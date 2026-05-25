import type { HLJSApi, Language, Mode } from 'highlight.js';
import pgsql from 'highlight.js/lib/languages/pgsql';

export const MTL_SQL_HIGHLIGHT_LANGUAGE = 'mtl-pgsql';

const MTL_NAMED_PARAM_MODE: Mode = {
  className: 'mtl-param',
  begin: /(?<!:):[A-Za-z_][A-Za-z0-9_]*/,
  relevance: 0,
};

export function registerMtlSqlHighlight(hljs: HLJSApi): void {
  if (hljs.getLanguage(MTL_SQL_HIGHLIGHT_LANGUAGE)) return;

  hljs.registerLanguage(MTL_SQL_HIGHLIGHT_LANGUAGE, (api): Language => {
    const language = pgsql(api);
    return {
      ...language,
      contains: [...(language.contains ?? []), MTL_NAMED_PARAM_MODE],
    };
  });
}
