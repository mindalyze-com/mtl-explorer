import { mkdir, copyFile, readFile, writeFile } from 'node:fs/promises';
import postcss from 'postcss';
import tailwind from '@tailwindcss/postcss';
await mkdir('assets', { recursive: true });
const styles = await postcss([tailwind({ optimize: { minify: true } })]).process(await readFile('styles/input.css', 'utf8'), { from: 'styles/input.css', to: 'assets/styles.css' });
await writeFile('assets/styles.css', styles.css);
await copyFile('node_modules/echarts/dist/echarts.min.js', 'assets/echarts.min.js');
await copyFile('node_modules/echarts/LICENSE', 'assets/echarts.LICENSE');
await copyFile('node_modules/echarts/NOTICE', 'assets/echarts.NOTICE');
await copyFile('node_modules/tailwindcss/LICENSE', 'assets/tailwind.LICENSE');
const iconNames = ['expand','sun-moon','x','filter','rotate-ccw','download','chevron-down','info'];
const symbols = await Promise.all(iconNames.map(async name => {
  const svg = await readFile('node_modules/lucide-static/icons/'+name+'.svg','utf8');
  const body = svg.match(/<svg\b[^>]*>([\s\S]*?)<\/svg>/)?.[1];
  if(!body)throw new Error('Invalid Lucide icon: '+name);
  return '<symbol id="'+name+'" viewBox="0 0 24 24">'+body+'</symbol>';
}));
await writeFile('assets/icons.svg','<svg xmlns="http://www.w3.org/2000/svg">'+symbols.join('')+'</svg>');
await copyFile('node_modules/lucide-static/LICENSE','assets/lucide.LICENSE');
