import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import * as echarts from 'echarts';

const source = await readFile(new URL('../templates/app.js',import.meta.url),'utf8');
const themeSource = await readFile(new URL('../templates/theme.js',import.meta.url),'utf8');
const template = await readFile(new URL('../templates/report.html',import.meta.url),'utf8');
const ids = [...template.matchAll(/id="([^"]+)"/g)].map(match=>match[1]);
class Element {
  constructor(){this.children=[];this.events={};this.textContent='';this.value='';this.className='';this.hidden=false;this.style={};this.attributes=new Map();const classes=new Set();this.classList={toggle(name,on){if(on)classes.add(name);else classes.delete(name);},contains:name=>classes.has(name)};}
  append(...items){this.children.push(...items);}
  replaceChildren(...items){this.children=[...items];}
  addEventListener(event,fn){this.events[event]=fn;}
  setAttribute(name,value){this.attributes.set(name,value);}
  getAttribute(name){return this.attributes.get(name)??null;}
  getBoundingClientRect(){return {top:100,bottom:132,height:100};}
  contains(element){return element===this||this.children.some(child=>child.contains(element));}
  showModal(){this.open=true;}
  close(){this.open=false;this.events.close?.();}
}
function bucket(requests,bytes,installations=0){return {requests,bytes,installations,client_logins:installations,coverage_pct:{login_ref:100,instance_type:100},statuses:{206:requests},latency_ms_upper_estimates:{p50:2,p95:8,p99:16}};}
function fixture(){
  const report={name:'custom',title:'Fixture',period:{from:'2026-10-01T00:00:00Z',to_exclusive:'2026-10-03T00:00:00Z',timezone:'UTC',group:'day'},generated_at:'2026-10-03T00:00:00Z',source:{files:1,parsed_records:11,health_requests:0,earliest:'2026-09-01T00:00:00Z',data_through:'2026-10-03T00:00:00Z'},
    totals:{candidate:bucket(8,700,1)},candidate_activity:{first_seen_installations:1},definitions:{installation:'A database, not a person'},versions:[{version:'1.524',requests:11,installations:1,builds:[]}],scopes:[{name:'custom',title:'Fixture'},{name:'last-week-hourly',title:'Last week · hourly'}],
    rows:[{start_utc:'2026-10-01T00:00:00Z',end_utc:'2026-10-02T00:00:00Z',observed_history:true,partial:false,first_seen_installations:1,traffic:{all:bucket(4,300,1),candidate:bucket(3,200,1),demo:bucket(1,100,1),internal:bucket(0,0),unclassified:bucket(0,0)}},
      {start_utc:'2026-10-02T00:00:00Z',end_utc:'2026-10-03T00:00:00Z',observed_history:true,partial:false,first_seen_installations:0,traffic:{all:bucket(7,550,1),candidate:bucket(5,500,1),demo:bucket(0,0),internal:bucket(2,50,1),unclassified:bucket(0,0)}}]};
  report.load_days=[
    {date:'2026-10-01',start_utc:report.rows[0].start_utc,end_utc:report.rows[0].end_utc,requests:4,bytes:300,observed_seconds:86400,first_seen_installations:1,peak_second:{at_utc:'2026-10-01T09:10:11Z',requests:2},busiest_hour:{start_utc:'2026-10-01T09:00:00Z',requests:3},peak_installation_hour:{start_utc:'2026-10-01T09:00:00Z',installations:1},peak_login_hour:{start_utc:'2026-10-01T09:00:00Z',client_logins:1}},
    {date:'2026-10-02',start_utc:report.rows[1].start_utc,end_utc:report.rows[1].end_utc,requests:7,bytes:550,observed_seconds:86400,first_seen_installations:0,peak_second:{at_utc:'2026-10-02T10:11:12Z',requests:5},busiest_hour:{start_utc:'2026-10-02T10:00:00Z',requests:7},peak_installation_hour:{start_utc:'2026-10-02T10:00:00Z',installations:1},peak_login_hour:{start_utc:'2026-10-02T10:00:00Z',client_logins:1}}
  ];
  for(const [index,row] of report.rows.entries())row.load={...report.load_days[index],average_requests_per_second:report.load_days[index].requests/86400};
  return report;
}
function harness(report=fixture(),{storage=new Map(),systemDark=false,storageBlocked=false,processing={},processingUnavailable=false,location='http://reports.test/reports/custom.html'}={}){
  const elements=new Map(ids.map(id=>[id,new Element()]));
  for(const match of template.matchAll(/<[^>]*id="([^"]+)"[^>]*\bhidden\b[^>]*>/g))elements.get(match[1]).hidden=true;
  for(const id of ids.filter(id=>id.startsWith('help-wrap-'))){const name=id.slice('help-wrap-'.length);elements.get(id).append(elements.get('help-'+name),elements.get('help-text-'+name));}
  elements.get('report-data').textContent=JSON.stringify(report);
  const charts=new Map();
  const classes=new Set(),warnings=[];
  const document={readyState:'complete',events:{},addEventListener(event,fn){this.events[event]=fn;},getElementById:id=>{assert.ok(elements.has(id),'Missing HTML element '+id);return elements.get(id);},createElement:()=>new Element(),querySelector:()=>new Element(),documentElement:{style:{},classList:{toggle(name,on){if(on)classes.add(name);else classes.delete(name);},contains:name=>classes.has(name)}}};
  const window={innerHeight:800,matchMedia:()=>({matches:systemDark}),location:{href:location},localStorage:{getItem(key){if(storageBlocked)throw new Error('Storage blocked');return storage.get(key)??null;},setItem(key,value){if(storageBlocked)throw new Error('Storage blocked');storage.set(key,value);}}};
  const fetchCalls=[];
  window.fetch=async(url,options)=>{fetchCalls.push({url,options});return {ok:!processingUnavailable,status:processingUnavailable?404:200,json:async()=>({generated_at:report.generated_at,...processing})};};
  const context={document,window,Intl,Date,URL,console:{...console,warn(...values){warnings.push(values);}},ResizeObserver:class{observe(){}},echarts:{init(element){const chart={setOption(option){chart.option=option;},getOption(){return chart.option;},resize(){chart.resized=true;},dispose(){chart.disposed=true;}};charts.set(element,chart);return chart;}}};
  window.echarts=context.echarts;
  vm.runInNewContext(themeSource,context);
  vm.runInNewContext(source,context);
  return {elements,charts,window,document,storage,warnings,fetchCalls};
}

test('first visit follows system appearance and ignores invalid saved values',()=>{
  for(const saved of [null,'invalid'])for(const systemDark of [false,true]){
    const storage=new Map(saved===null?[]:[['mtl-map-reports-theme',saved]]);
    const {document}=harness(fixture(),{storage,systemDark});
    assert.equal(document.documentElement.classList.contains('dark'),systemDark);
    assert.equal(document.documentElement.style.colorScheme,systemDark?'dark':'light');
  }
});
test('chosen dark theme survives period navigation and reload',()=>{
  const storage=new Map();
  const first=harness(fixture(),{storage});
  first.elements.get('theme').events.click();
  first.elements.get('scope').value='last-week-hourly.html';first.elements.get('scope').events.change();
  assert.equal(first.window.location.href,'last-week-hourly.html');
  assert.equal(storage.get('mtl-map-reports-theme'),'dark');
  for(const systemDark of [false,true]){
    const next=harness(fixture(),{storage,systemDark});
    assert.equal(next.document.documentElement.classList.contains('dark'),true);
    assert.equal(next.document.documentElement.style.colorScheme,'dark');
    assert.equal(next.charts.get(next.elements.get('requests')).option.textStyle.color,'#f1f5f9');
  }
});
test('chosen light theme overrides a dark system across pages',()=>{
  const storage=new Map();
  const first=harness(fixture(),{storage,systemDark:true});
  first.elements.get('theme').events.click();
  assert.equal(storage.get('mtl-map-reports-theme'),'light');
  const next=harness(fixture(),{storage,systemDark:true});
  assert.equal(next.document.documentElement.classList.contains('dark'),false);
  assert.equal(next.document.documentElement.style.colorScheme,'light');
  assert.equal(next.charts.get(next.elements.get('requests')).option.textStyle.color,'#0f172a');
});
test('blocked browser storage preserves working theme controls and charts',()=>{
  const {elements,document,charts,warnings}=harness(fixture(),{storageBlocked:true});
  elements.get('theme').events.click();
  assert.equal(document.documentElement.classList.contains('dark'),true);
  assert.equal(charts.size,9);
  assert.equal(warnings.length,2);
});
test('saved appearance is applied before the stylesheet loads',()=>{
  assert.ok(template.indexOf('__THEME__')<template.indexOf('assets/styles.css'));
});

test('all nine graph options initialise against existing HTML elements',()=>{
  const {elements,charts}=harness();
  assert.equal(charts.size,9);
  const option=charts.get(elements.get('requests')).option;
  assert.equal(option.series.flatMap(series=>series.data).reduce((a,b)=>a+b,0),11);
  assert.equal(elements.get('summary').children[2].children[1].textContent,'1');
  for(const chart of charts.values())assert.equal(chart.option.dataZoom,undefined);
});
test('chart explanations support hover, focus, dismissal and a single visible tooltip',()=>{
  const {elements,document}=harness();
  for(const name of ['requests','data','rate','active','first','logins','coverage','statuses','latency']){
    assert.equal(elements.get('help-text-'+name).hidden,true);
    assert.ok(elements.get('help-copy-'+name).textContent.length>50);
    elements.get('help-wrap-'+name).events.mouseenter();
    assert.equal(elements.get('help-text-'+name).hidden,false);
    elements.get('help-wrap-'+name).events.mouseleave();
    assert.equal(elements.get('help-text-'+name).hidden,true);
  }
  const active=elements.get('help-active'),tip=elements.get('help-text-active');
  document.activeElement=active;active.events.focus();
  elements.get('help-wrap-active').events.mouseleave();
  assert.equal(tip.hidden,false);assert.equal(active.getAttribute('aria-expanded'),'true');
  document.events.keydown({key:'Escape',preventDefault(){}});
  assert.equal(tip.hidden,true);assert.equal(active.getAttribute('aria-expanded'),'false');
  elements.get('help-wrap-active').events.mouseenter();elements.get('help-wrap-logins').events.mouseenter();
  assert.equal(tip.hidden,true);assert.equal(elements.get('help-text-logins').hidden,false);
});
test('tapped chart help stays open until toggled or clicked outside and flips when space is limited',()=>{
  const {elements,document,window}=harness();
  const button=elements.get('help-active'),tip=elements.get('help-text-active');
  window.innerHeight=160;button.events.focus();button.events.click();
  assert.equal(tip.classList.contains('help-above'),true);
  elements.get('help-wrap-active').events.mouseleave();button.events.blur();
  assert.equal(tip.hidden,false);
  document.events.click({target:button});assert.equal(tip.hidden,false);
  button.events.click();assert.equal(tip.hidden,true);
  button.events.click();document.events.click({target:elements.get('from')});assert.equal(tip.hidden,true);
});
test('expanded help describes the selected chart and Escape dismisses help before closing the chart',()=>{
  const report=fixture();report.period.group='hour';report.period.timezone='Europe/Zurich';
  const {elements,document}=harness(report);
  elements.get('expand-active').events.click();elements.get('help-expanded').events.focus();
  const copy=elements.get('help-copy-expanded').textContent;
  assert.match(copy,/application database/);assert.match(copy,/HTTP 200–399/);assert.match(copy,/do not add the counts/);
  assert.match(copy,/one hour in Europe\/Zurich/);
  document.events.keydown({key:'Escape',preventDefault(){}});
  assert.equal(elements.get('help-text-expanded').hidden,true);assert.equal(elements.get('chart-dialog').open,true);
  document.events.keydown({key:'Escape',preventDefault(){}});assert.equal(elements.get('chart-dialog').open,false);
  elements.get('expand-coverage').events.click();
  assert.match(elements.get('help-copy-expanded').textContent,/field availability/);
  assert.equal(elements.get('help-expanded').getAttribute('aria-label'),'Explain Collection coverage');
});
const hover = (chart,index=0) => chart.option.tooltip.formatter(chart.option.series.map(series=>({dataIndex:index,seriesName:series.name,value:series.data[index],marker:'<span class="series-marker"></span>'})));
test('every normal and expanded chart shows a weekday and European date in its tooltip',()=>{
  const {elements,charts}=harness();
  for(const name of ['requests','data','rate','active','first','logins','coverage','statuses','latency']){
    const chart=charts.get(elements.get(name));
    assert.match(hover(chart),/Thursday, 1 October 2026/);
    assert.match(hover(chart),/>UTC</);
    elements.get('expand-'+name).events.click();
    assert.equal(hover(charts.get(elements.get('expanded-chart'))),hover(chart));
    elements.get('close-chart').events.click();
  }
});
test('hourly tooltips use the report timezone, local weekday and a 24-hour interval on every chart',()=>{
  const report=fixture();Object.assign(report.period,{group:'hour',timezone:'Europe/Zurich',from:'2026-10-01T23:00:00Z',to_exclusive:'2026-10-02T01:00:00Z'});
  Object.assign(report.rows[0],{start_utc:report.period.from,end_utc:'2026-10-02T00:00:00Z'});
  Object.assign(report.rows[1],{start_utc:'2026-10-02T00:00:00Z',end_utc:report.period.to_exclusive});
  for(const chart of harness(report).charts.values()){
    assert.match(hover(chart),/Friday, 2 October 2026/);
    assert.match(hover(chart),/01:00 GMT\+2 – 02:00 GMT\+2 · Europe\/Zurich/);
    assert.doesNotMatch(hover(chart),/\b[AP]M\b|10\/02/);
    assert.match(chart.option.xAxis.data[0],/01:00/);
  }
});
test('repeated daylight-saving hours retain their distinct offsets',()=>{
  const report=fixture();Object.assign(report.period,{group:'hour',timezone:'Europe/Zurich',from:'2026-10-25T00:00:00Z',to_exclusive:'2026-10-25T02:00:00Z'});
  Object.assign(report.rows[0],{start_utc:report.period.from,end_utc:'2026-10-25T01:00:00Z'});
  Object.assign(report.rows[1],{start_utc:'2026-10-25T01:00:00Z',end_utc:report.period.to_exclusive});
  const {elements,charts}=harness(report),chart=charts.get(elements.get('requests'));
  assert.match(hover(chart),/Sunday, 25 October 2026/);
  assert.match(hover(chart),/02:00 GMT\+2 – 02:00 GMT\+1/);
  assert.match(hover(chart,1),/02:00 GMT\+1 – 03:00 GMT\+1/);
});
test('weekly and monthly hover ranges respect report edges and their exclusive end',()=>{
  for(const group of ['week','month']){
    const report=fixture();report.period.group=group;
    report.rows=[{...report.rows[0],start_utc:'2026-09-28T00:00:00Z',end_utc:'2026-11-01T00:00:00Z',partial:true}];
    const {elements,charts}=harness(report);
    const tooltip=hover(charts.get(elements.get('requests')));
    assert.match(tooltip,/Thursday, 1 October 2026 – Friday, 2 October 2026/);
    assert.match(tooltip,/Partial bucket/);
    assert.doesNotMatch(tooltip,/28 September|3 October|1 November/);
  }
});
test('filtered hover indices follow the visible buckets and escape series text',()=>{
  const {elements,charts}=harness();
  elements.get('from').value='2026-10-02';elements.get('to').value='2026-10-02';elements.get('filter').events.click();
  const chart=charts.get(elements.get('requests'));
  assert.match(hover(chart),/Friday, 2 October 2026/);
  assert.doesNotMatch(hover(chart),/Thursday/);
  const tooltip=chart.option.tooltip.formatter([{dataIndex:0,seriesName:'<img src=x>',value:null,marker:''}]);
  assert.match(tooltip,/&lt;img src=x&gt;/);assert.match(tooltip,/Unavailable/);
  assert.equal(chart.option.tooltip.formatter([]),'');
});
test('date filtering changes graph values without adding daily unique counts',()=>{
  const {elements,charts}=harness();
  elements.get('from').value='2026-10-02';elements.get('to').value='2026-10-02';elements.get('filter').events.click();
  assert.equal(charts.get(elements.get('requests')).option.series.flatMap(series=>series.data).reduce((a,b)=>a+b,0),7);
  assert.equal(elements.get('summary').children[2].children[1].textContent,'Full report only');
  elements.get('reset').events.click();
  assert.equal(elements.get('summary').children[0].children[1].textContent,'11');
});
test('invalid dates preserve graphs and provide a clear message',()=>{
  const {elements,charts}=harness();
  const before=charts.get(elements.get('requests')).option;
  elements.get('from').value='2026-10-03';elements.get('to').value='2026-10-01';elements.get('filter').events.click();
  assert.match(elements.get('filter-note').textContent,/valid/);
  assert.equal(charts.get(elements.get('requests')).option,before);
});
test('preset navigation and aggregate download use generated static files',()=>{
  const {elements,window}=harness();
  elements.get('scope').value='last-week-hourly.html';elements.get('scope').events.change();
  assert.equal(window.location.href,'last-week-hourly.html');
  assert.equal(elements.get('download').href,'custom.json');
});
test('missing history stays null rather than becoming zero usage',()=>{
  const report=fixture();report.rows[0].observed_history=false;
  const {elements,charts}=harness(report);
  assert.equal(charts.get(elements.get('requests')).option.series[0].data[0],null);
});
test('every chart expands to its own large view and disposes on close',()=>{
  const {elements,charts}=harness();
  for(const name of ['requests','data','rate','active','first','logins','coverage','statuses','latency']){
    elements.get('expand-'+name).events.click();
    assert.equal(elements.get('chart-dialog').open,true);
    const expanded=charts.get(elements.get('expanded-chart'));
    assert.deepEqual(expanded.option,charts.get(elements.get(name)).option);
    assert.equal(expanded.resized,true);
    elements.get('close-chart').events.click();
    assert.equal(elements.get('chart-dialog').open,false);
    assert.equal(expanded.disposed,true);
  }
});
test('peak metrics follow exact dates even within one weekly graph bucket',()=>{
  const report=fixture();report.period.group='week';
  report.rows=[{...report.rows[0],start_utc:'2026-09-28T00:00:00Z',end_utc:'2026-10-05T00:00:00Z',traffic:{all:bucket(11,850),candidate:bucket(8,700,1)}}];
  const {elements,charts}=harness(report);
  assert.equal(elements.get('load-summary').children[2].children[1].textContent,'5');
  elements.get('from').value='2026-10-01';elements.get('to').value='2026-10-01';elements.get('filter').events.click();
  assert.equal(elements.get('summary').children[0].children[1].textContent,'4');
  assert.equal(elements.get('summary').children[2].children[1].textContent,'Full report only');
  assert.equal(elements.get('load-summary').children[2].children[1].textContent,'2');
  assert.match(elements.get('load-summary').children[2].children[2].textContent,/09:10:11/);
  assert.equal(elements.get('load-summary').children[3].children[1].textContent,'3 requests');
  assert.equal(charts.get(elements.get('rate')).option.series[1].data[0],2);
});
test('empty date selections show unavailable rates and reset restores peaks',()=>{
  const {elements}=harness();
  elements.get('from').value='2026-11-01';elements.get('to').value='2026-11-02';elements.get('filter').events.click();
  for(const card of elements.get('load-summary').children)assert.equal(card.children[1].textContent,'Unavailable');
  elements.get('reset').events.click();
  assert.equal(elements.get('load-summary').children[2].children[1].textContent,'5');
  assert.notEqual(elements.get('load-summary').children[0].children[1].textContent,'0');
});
test('long sections default to closed disclosures with a release preview',()=>{
  for(const name of ['versions','definitions','technical']){
    const tag=template.match(new RegExp('<details id="'+name+'-details"[^>]*>'))?.[0];
    assert.ok(tag);assert.doesNotMatch(tag,/\bopen\b/);
  }
  const {elements}=harness();
  assert.match(elements.get('versions-preview').textContent,/1.524/);
});
test('technical details show file sizing, full-scan counts and matching generation time',async()=>{
  const report=fixture();Object.assign(report.source,{files:15,compressed_files:11,plain_files:4,stored_bytes:2048,read_bytes:8192,
    lines:100,parsed_records:100,map_requests:80,health_requests:15,other_requests:5,scan_seconds:1,nginx_records:100,caddy_records:0});
  const {elements}=harness(report,{processing:{total_seconds:1.25,render_seconds:.1,peak_process_rss_bytes:1048576,reports_generated:5,output_buckets:300}});
  await new Promise(resolve=>setImmediate(resolve));
  const values=new Map(elements.get('technical-metrics').children.map(entry=>entry.children.map(child=>child.textContent)));
  assert.equal(values.get('Stored log size'),'2 KiB');assert.equal(values.get('Decoded bytes streamed'),'8 KiB');
  assert.equal(values.get('Access records evaluated'),'100');assert.equal(values.get('Total processing time'),'1.250 s');
  assert.equal(values.get('Peak reporter process memory'),'1 MiB');assert.equal(values.get('Scan throughput'),'100 records/s');
  assert.match(elements.get('technical-preview').textContent,/15 files.*100 records.*1.250 s/);
});
test('unavailable or mismatched processing metadata preserves charts and labels missing timings',async()=>{
  for(const options of [{processingUnavailable:true},{processing:{generated_at:'different-generation',total_seconds:999}}]){
    const {elements,charts,warnings}=harness(fixture(),options);
    await new Promise(resolve=>setImmediate(resolve));
    const values=new Map(elements.get('technical-metrics').children.map(entry=>entry.children.map(child=>child.textContent)));
    assert.equal(values.get('Total processing time'),'Not recorded');assert.equal(charts.size,9);assert.equal(warnings.length,1);
  }
});
test('processing metadata URLs omit embedded credentials and use the existing authenticated origin',async()=>{
  const {fetchCalls,warnings}=harness(fixture(),{location:'http://reader:test-only@reports.test/reports/custom.html'});
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(fetchCalls[0].url,'http://reports.test/reports/assets/processing.json');
  assert.equal(fetchCalls[0].options.credentials,'same-origin');assert.equal(warnings.length,0);
});
test('all controls reference bundled Lucide symbols and keep accessible labels',async()=>{
  const sprite=await readFile(new URL('../assets/icons.svg',import.meta.url),'utf8');
  for(const match of template.matchAll(/href="assets\/icons.svg#([^"]+)"/g))assert.ok(sprite.includes('id="'+match[1]+'"'));
  assert.match(template,/id="theme"[^>]*aria-label="Change theme"/);
  assert.match(template,/id="expand-rate"[^>]*aria-label="Expand chart"/);
  assert.ok(sprite.length<10000);
});
test('Escape closes an expanded chart without changing the selected data',()=>{
  const {elements,charts,document}=harness();
  elements.get('expand-requests').events.click();
  document.events.keydown({key:'Escape',preventDefault(){}});
  assert.equal(elements.get('chart-dialog').open,false);
  assert.equal(charts.get(elements.get('requests')).option.series.flatMap(s=>s.data).reduce((a,b)=>a+b,0),11);
});
test('the actual chart library renders every plot as SVG without a browser',()=>{
  const {charts}=harness();
  for(const chart of charts.values()){
    const instance=echarts.init(null,null,{renderer:'svg',ssr:true,width:640,height:320});
    const option=chart.option;option.aria={enabled:false};
    instance.setOption(option);
    assert.match(instance.renderToSVGString(),/<svg/);
    instance.dispose();
  }
});
