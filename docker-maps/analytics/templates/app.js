(() => {
  const report = JSON.parse(document.getElementById('report-data').textContent);
  const labels = {candidate:'Candidate external',demo:'Demo instances',internal:'Development / tests',unclassified:'Unclassified'};
  const integer = new Intl.NumberFormat('en');
  const decimal = new Intl.NumberFormat('en',{maximumFractionDigits:2});
  const rateNumber = new Intl.NumberFormat('en',{maximumSignificantDigits:3});
  const date = new Intl.DateTimeFormat('en-GB',{dateStyle:'medium',timeStyle:'short',timeZone:report.period.timezone});
  const moment = new Intl.DateTimeFormat('en-GB',{year:'numeric',month:'short',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23',timeZoneName:'shortOffset',timeZone:report.period.timezone});
  const hoverDate = new Intl.DateTimeFormat('en-GB',{weekday:'long',day:'numeric',month:'long',year:'numeric',timeZone:report.period.timezone});
  const hoverTime = new Intl.DateTimeFormat('en-GB',{hour:'2-digit',minute:'2-digit',hourCycle:'h23',timeZoneName:'shortOffset',timeZone:report.period.timezone});
  const axisDate = new Intl.DateTimeFormat('en-GB',{day:'2-digit',month:'short',...(report.period.group==='hour'?{hour:'2-digit',minute:'2-digit',hourCycle:'h23',timeZoneName:'shortOffset'}:{}),timeZone:report.period.timezone});
  const localDay = value => new Intl.DateTimeFormat('en-CA',{year:'numeric',month:'2-digit',day:'2-digit',timeZone:report.period.timezone}).format(new Date(value));
  const charts = new Map();
  const titles = {requests:'Map requests',data:'Data served',rate:'Request rate and bursts',active:'Active candidate installations',first:'First-observed candidate installations',logins:'Observed client logins',coverage:'Collection coverage',statuses:'Response statuses',latency:'Server response time'};
  const chartDescriptions = {
    requests:'Number of requests for .pmtiles map files, including retries and failed requests. Stacked bars separate candidate external, demo, development/test and unclassified traffic.',
    data:'Bytes sent for map requests, in decimal gigabytes (1 GB = 1,000,000,000 bytes). Repeated and partial-file requests count as traffic too; this is transferred data, not the size of unique map files.',
    rate:'Average requests per second across available observation time, including idle time, and the largest number logged in one fixed second. These are logged rates, not concurrent connections or rolling peaks. Rate calculations follow the selected dates even within a weekly or monthly bucket.',
    active:'Distinct candidate external installation IDs with at least one successful map response (HTTP 200–399) in a bucket. One ID represents an application database. Repeated requests count once per bucket; the same database can appear in several buckets, so do not add the counts. Database resets may create new IDs. Candidate external excludes recognised demo and development/test traffic. This does not count people or simultaneous users.',
    first:'Candidate external installation IDs first observed making a successful map request (HTTP 200–399). The date comes from retained logs and the private first-seen ledger, not account registration. Database resets can create new IDs.',
    logins:'Distinct installation/login-reference pairs with successful map responses (HTTP 200–399), split by traffic category. Only logins fetching maps with a recorded reference are counted. This does not count all accounts, sign-in events or simultaneous users.',
    coverage:'Percentage of all map requests carrying each collection field: login reference, instance type, image build, requested range and forwarded IP. This measures field availability. Older clients or log formats may omit fields; missing data does not prove a feature is unused.',
    statuses:'Number of map requests for each HTTP response status. HTTP 206 is normal for partial-file requests; 4xx indicates client errors and 5xx server errors. No recorded status means the historical log did not supply one.',
    latency:'P50, P95 and P99 upper estimates of logged server request duration, in milliseconds. For example, 95% of measured requests fall at or below the P95 histogram ceiling. Fixed histogram buckets make these approximate. This measures the server request, not browser rendering or the complete user experience.'
  };
  let expanded = null;
  let expandedName = null;
  let selected = report.rows;
  let selectedDays = report.load_days;
  let ready = false;
  let dark = window.mtlMapReportTheme.isDark();
  const get = id => document.getElementById(id);
  const text = (id,value) => { get(id).textContent = value; };
  const emptyBucket = {requests:0,bytes:0,installations:0,client_logins:0,coverage:{},coverage_pct:{},statuses:{},latency_ms_upper_estimates:{}};
  const traffic = (row,key='all') => row.traffic[key] || emptyBucket;
  const coverage = (row,key) => traffic(row).requests ? traffic(row).coverage_pct[key] || 0 : null;
  const fullRange = () => selected.length === report.rows.length && selected.every((row,i) => row === report.rows[i]) && selectedDays.length === report.load_days.length && selectedDays.every((row,i) => row === report.load_days[i]);
  const peak = (items,key,metric) => items.map(item=>item[key]).filter(Boolean).reduce((best,item)=>!best||item[metric]>best[metric]?item:best,null);
  const escapeHtml = value => String(value).replace(/[&<>"']/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));

  function bucketDate(row) {
    const start=new Date(Math.max(Date.parse(row.start_utc),Date.parse(report.period.from)));
    const end=new Date(Math.min(Date.parse(row.end_utc),Date.parse(report.period.to_exclusive)));
    let title=hoverDate.format(start),detail=report.period.timezone;
    if(report.period.group==='hour') {
      const endDay=localDay(start)===localDay(end)?'':hoverDate.format(end)+' · ';
      detail=hoverTime.format(start)+' – '+endDay+hoverTime.format(end)+' · '+detail;
    } else if(report.period.group==='week'||report.period.group==='month') {
      const last=new Date(end.getTime()-1);
      if(localDay(start)!==localDay(last))title+=' – '+hoverDate.format(last);
    }
    return {title,detail:detail+(row.partial?' · Partial bucket':'')};
  }

  function summary() {
    get('summary').replaceChildren();
    const requests = selectedDays.reduce((sum,row) => sum+row.requests,0);
    const bytes = selectedDays.reduce((sum,row) => sum+row.bytes,0);
    const cards = [['Map requests',integer.format(requests)],['Data served',decimal.format(bytes/1e9)+' GB'],
      ['Candidate installations',fullRange() ? integer.format(report.totals.candidate.installations) : 'Full report only'],
      ['First observed',integer.format(selectedDays.reduce((sum,row) => sum+row.first_seen_installations,0))]];
    for (const [index,[label,value]] of cards.entries()) {
      const card = document.createElement('div');card.className='report-panel space-y-1';
      const a=document.createElement('p');a.className='muted';a.textContent=label;
      const b=document.createElement('p');b.className='metric';b.textContent=value;card.append(a,b);
      if(fullRange()&&report.comparison?.history_covers_period) {
        const previous=report.comparison.totals;
        const pairs=[[requests,previous.all.requests],[bytes,previous.all.bytes],[report.totals.candidate.installations,previous.candidate.installations],[report.candidate_activity.first_seen_installations,report.comparison.candidate_activity.first_seen_installations]];
        const [now,before]=pairs[index];
        const c=document.createElement('p');c.className='muted';c.textContent=before?decimal.format(100*(now/before-1))+'% vs previous period':'No previous activity';card.append(c);
      }
      get('summary').append(card);
    }
    text('filter-note',fullRange() ? 'Partial edge buckets are marked *. Installation IDs represent databases, not verified people.' : 'Summary and peak metrics follow the selected dates. Graphs use whole displayed buckets; unique installation totals and the release table refer to the full report period.');
    const duration = selectedDays.reduce((sum,row)=>sum+row.observed_seconds,0);
    const second = peak(selectedDays,'peak_second','requests');
    const hour = peak(selectedDays,'busiest_hour','requests');
    const installations = peak(selectedDays,'peak_installation_hour','installations');
    const logins = peak(selectedDays,'peak_login_hour','client_logins');
    const stamp = value => value ? moment.format(new Date(value.at_utc||value.start_utc))+(value.partial?' · partial hour':'') : duration?'No activity observed':'Outside available history';
    const loadCards = [
      ['Average requests/s',duration?rateNumber.format(requests/duration):'Unavailable','Within retained observation timestamps'],
      ['Average requests/hour',duration?rateNumber.format(3600*requests/duration):'Unavailable','Includes idle time'],
      ['Peak logged requests/s',duration?integer.format(second?.requests||0):'Unavailable',stamp(second)],
      ['Busiest hour',duration?integer.format(hour?.requests||0)+' requests':'Unavailable',stamp(hour)],
      ['Peak installations/hour',duration?integer.format(installations?.installations||0):'Unavailable',stamp(installations)],
      ['Peak observed logins/hour',duration?integer.format(logins?.client_logins||0):'Unavailable',stamp(logins)]
    ];
    get('load-summary').replaceChildren();
    for(const [label,value,detail] of loadCards){
      const card=document.createElement('div');card.className='report-panel space-y-1';
      for(const [content,className] of [[label,'muted'],[value,'metric'],[detail,'muted']]){const line=document.createElement('p');line.className=className;line.textContent=content;card.append(line);}
      get('load-summary').append(card);
    }
  }

  function chartOption(name) {
    const muted=dark?'#94a3b8':'#475569',grid=dark?'#334155':'#e2e8f0',foreground=dark?'#f1f5f9':'#0f172a';
    const groups=Object.keys(labels);
    const points=selected.map(row=>axisDate.format(new Date(row.start_utc))+(row.partial?' *':''));
    const bucketDates=selected.map(bucketDate);
    const formatValue=value=>value==null?'Unavailable':(name==='rate'?rateNumber:decimal).format(value);
    let series=[],unit='',maximum;
    const categorySeries=(field,divisor=1) => groups.map(key => ({name:labels[key],type:'bar',stack:'traffic',data:selected.map(row => row.observed_history ? traffic(row,key)[field]/divisor : null)}));
    if(name==='requests') {series=categorySeries('requests');unit='Requests';}
    if(name==='data') {series=categorySeries('bytes',1e9);unit='GB';}
    if(name==='rate') {
      unit='Requests/s';
      const loads=selected.map(row=>{
        if(report.period.group==='hour'||report.period.group==='day')return row.load;
        const days=selectedDays.filter(day=>day.start_utc>=row.start_utc&&day.start_utc<row.end_utc);
        const seconds=days.reduce((sum,day)=>sum+day.observed_seconds,0);
        return {observed_seconds:seconds,average_requests_per_second:seconds?days.reduce((sum,day)=>sum+day.requests,0)/seconds:null,peak_second:peak(days,'peak_second','requests')};
      });
      series=[{name:'Average in observed window',type:'line',showSymbol:false,data:loads.map(load=>load.average_requests_per_second)},
              {name:'Peak logged second',type:'line',showSymbol:false,data:loads.map(load=>load.observed_seconds?load.peak_second?.requests||0:null)}];
    }
    if(name==='active'||name==='first') {unit='Installations';series=[{name:name==='active'?'Active candidates':'First observed',type:'bar',data:selected.map(row => row.observed_history ? name==='active'?traffic(row,'candidate').installations:row.first_seen_installations : null)}];}
    if(name==='logins') {unit='Logins observed';series=groups.map(key=>({name:labels[key],type:'bar',stack:'logins',data:selected.map(row=>row.observed_history?traffic(row,key).client_logins:null)}));}
    if(name==='coverage') {
      unit='%';maximum=100;
      series=[['login_ref','Login reference'],['instance_type','Instance type'],['build','Image build'],['request_range','Requested range'],['forwarded_ip','Forwarded IP']].map(([key,label])=>({name:label,type:'line',showSymbol:false,data:selected.map(row=>row.observed_history?coverage(row,key):null)}));
    }
    if(name==='statuses') {
      unit='Requests';
      const codes=[...new Set(selected.flatMap(row=>Object.keys(traffic(row).statuses)))].sort();
      series=codes.map(code=>({name:code==='0'?'No recorded status':'HTTP '+code,type:'bar',stack:'statuses',data:selected.map(row=>row.observed_history?(traffic(row).statuses[code]||0):null)}));
    }
    if(name==='latency') {unit='ms (upper estimates)';series=['p50','p95','p99'].map(key=>({name:key.toUpperCase(),type:'line',showSymbol:false,data:selected.map(row=>traffic(row).latency_ms_upper_estimates[key]??null)}));}
    return {
      animation:false,backgroundColor:'transparent',textStyle:{color:foreground,fontFamily:'system-ui, sans-serif'},
      color:dark?['#60a5fa','#fb923c','#a78bfa','#94a3b8','#34d399']:['#2563eb','#ea580c','#7c3aed','#64748b','#059669'],
      aria:{enabled:true},tooltip:{trigger:'axis',confine:true,backgroundColor:dark?'#0f172a':'#ffffff',textStyle:{color:foreground},valueFormatter:formatValue,
        formatter:items=>{
          const entries=Array.isArray(items)?items:[items];
          const stamp=bucketDates[entries[0]?.dataIndex];
          if(!stamp)return '';
          const heading='<div class="chart-tooltip" style="max-width:min(360px,calc(100vw - 80px));white-space:normal;line-height:1.5"><div style="font-weight:600">'+escapeHtml(stamp.title)+'</div><div style="font-size:12px;margin:2px 0 8px">'+escapeHtml(stamp.detail)+'</div>';
          return heading+entries.map(item=>'<div>'+(item.marker||'')+escapeHtml(item.seriesName)+'<strong style="float:right;margin-left:20px">'+escapeHtml(formatValue(item.value))+'</strong></div>').join('')+'</div>';
        }},
      legend:{top:0,type:'scroll',textStyle:{color:muted}},grid:{left:64,right:16,top:58,bottom:24},
      xAxis:{type:'category',data:points,axisLabel:{color:muted,hideOverlap:true},axisLine:{lineStyle:{color:grid}}},
      yAxis:{type:'value',min:0,max:maximum,name:unit,nameTextStyle:{color:muted},axisLabel:{color:muted,formatter:value=>value>=1000?decimal.format(value/1000)+'k':(name==='rate'?rateNumber:decimal).format(value)},splitLine:{lineStyle:{color:grid}}},
      series
    };
  }

  function draw() {
    summary();
    if(!ready)return;
    for(const [name,chart] of charts)chart.setOption(chartOption(name),true);
    if(expanded)expanded.setOption(charts.get(expandedName).getOption(),true);
  }
  const dialog = get('chart-dialog');
  let openHelp=null,pinnedHelp=null;
  const helpControls=new Map();
  const helpText=name=>chartDescriptions[name]+' Each point covers one '+report.period.group+' in '+report.period.timezone+'.'+(name==='rate'?'':' Date filters select whole displayed buckets.');
  function hideHelp() {
    if(openHelp){const control=helpControls.get(openHelp);control.tooltip.hidden=true;control.button.setAttribute('aria-expanded','false');}
    openHelp=null;pinnedHelp=null;
  }
  for(const name of [...Object.keys(titles),'expanded']) {
    const wrapper=get('help-wrap-'+name),button=get('help-'+name),tooltip=get('help-text-'+name),copy=get('help-copy-'+name);
    helpControls.set(name,{wrapper,button,tooltip});
    if(name!=='expanded')text('help-copy-'+name,helpText(name));
    const show=()=>{
      if(openHelp!==name){hideHelp();openHelp=name;}
      tooltip.hidden=false;button.setAttribute('aria-expanded','true');
      tooltip.classList.toggle('help-above',false);
      copy.style.maxHeight='';
      const bounds=button.getBoundingClientRect(),below=window.innerHeight-bounds.bottom;
      const above=tooltip.getBoundingClientRect().height>below&&bounds.top>below;
      copy.style.maxHeight=Math.max(0,(above?bounds.top:below)-24)+'px';
      tooltip.classList.toggle('help-above',above);
    };
    wrapper.addEventListener('mouseenter',show);
    wrapper.addEventListener('mouseleave',()=>{if(openHelp===name&&pinnedHelp!==name&&document.activeElement!==button)hideHelp();});
    button.addEventListener('focus',show);
    button.addEventListener('blur',()=>{if(openHelp===name&&pinnedHelp!==name)hideHelp();});
    button.addEventListener('click',()=>{if(pinnedHelp===name)hideHelp();else{show();pinnedHelp=name;}});
  }
  document.addEventListener('click',event=>{if(openHelp&&!helpControls.get(openHelp).wrapper.contains(event.target))hideHelp();},true);
  for(const name of Object.keys(titles))get('expand-'+name).addEventListener('click',()=>{
    if(!ready)return;
    expandedName=name;
    text('expanded-title',titles[name]);
    text('expanded-period',get('period').textContent);
    text('help-copy-expanded',helpText(name));
    get('help-expanded').setAttribute('aria-label','Explain '+titles[name]);
    hideHelp();
    dialog.showModal();
    expanded=echarts.init(get('expanded-chart'),null,{renderer:'svg'});
    expanded.setOption(charts.get(name).getOption(),true);
    expanded.resize();
  });
  get('close-chart').addEventListener('click',()=>dialog.close());
  dialog.addEventListener('close',()=>{hideHelp();if(expanded)expanded.dispose();expanded=null;expandedName=null;});
  document.addEventListener('keydown',event=>{if(event.key==='Escape'){if(openHelp){event.preventDefault();hideHelp();}else if(dialog.open){event.preventDefault();dialog.close();}}});
  const scope=get('scope');
  if(report.demo_dataset)get('demo-note').hidden=false;
  for(const item of report.scopes) {const option=document.createElement('option');option.value=item.name+'.html';option.textContent=item.title;option.selected=item.name===report.name;scope.append(option);}
  scope.addEventListener('change',()=>{window.location.href=scope.value;});
  get('download').href=report.name+'.json';
  text('period',report.title+' · '+date.format(new Date(report.period.from))+' to '+date.format(new Date(report.period.to_exclusive))+' · '+report.period.timezone);
  text('freshness','Generated '+date.format(new Date(report.generated_at))+' · Data through '+(report.source.data_through?date.format(new Date(report.source.data_through)):'no records yet'));
  text('history-note','Available retained history starts '+(report.source.earliest?date.format(new Date(report.source.earliest)):'after the first logged request')+'. Periods outside that history are unavailable.'+(report.comparison&&!report.comparison.history_covers_period?' The preceding comparison period is not fully covered.':''));
  const reset=()=>{selected=report.rows;selectedDays=report.load_days;get('from').value=localDay(report.period.from);get('to').value=localDay(new Date(new Date(report.period.to_exclusive).getTime()-1).toISOString());draw();};
  get('filter').addEventListener('click',()=>{
    if(!get('from').value||!get('to').value||get('from').value>get('to').value){text('filter-note','Select a valid from/through date range.');return;}
    selected=report.rows.filter(row=>localDay(row.start_utc)<=get('to').value&&localDay(new Date(new Date(row.end_utc).getTime()-1).toISOString())>=get('from').value);
    selectedDays=report.load_days.filter(day=>day.date>=get('from').value&&day.date<=get('to').value);
    draw();
  });
  get('reset').addEventListener('click',reset);
  get('theme').addEventListener('click',()=>{dark=!dark;window.mtlMapReportTheme.setDark(dark);draw();});
  for(const value of report.versions) {
    const row=document.createElement('tr');
    for(const cell of [value.version,integer.format(value.requests),integer.format(value.installations),value.builds.join(', ')||'Unavailable']){const td=document.createElement('td');td.textContent=cell;row.append(td);}
    get('versions').append(row);
  }
  text('versions-preview',report.versions.length?integer.format(report.versions.length)+' releases observed · Most requests: '+report.versions[0].version:'No release information observed');
  for(const [key,value] of Object.entries(report.definitions)) {
    const dt=document.createElement('dt');dt.className='text-sm font-medium';dt.textContent=key.replaceAll('_',' ');
    const dd=document.createElement('dd');dd.className='muted';dd.textContent=value;get('definitions').append(dt,dd);
  }
  const bytesLabel = value => {
    if(value==null)return 'Not recorded';
    const units=['B','KiB','MiB','GiB','TiB'];let unit=0;
    while(value>=1024&&unit<units.length-1){value/=1024;unit++;}
    return decimal.format(value)+' '+units[unit];
  };
  const secondsLabel = value => value==null?'Not recorded':new Intl.NumberFormat('en',{minimumFractionDigits:3,maximumFractionDigits:3}).format(value)+' s';
  let processing = null;
  function technicalDetails() {
    const source=report.source;
    const count=value=>value==null?'Not recorded':integer.format(value);
    const other=source.other_requests??Math.max(0,(source.parsed_records||0)-(source.map_requests||0)-(source.health_requests||0)-(source.records_after_cutoff||0));
    const rows=[
      ['Access-log files',count(source.files)],['Gzip / plain files',count(source.compressed_files)+' / '+count(source.plain_files)],
      ['Stored log size',bytesLabel(source.stored_bytes)],['Decoded bytes streamed',bytesLabel(source.read_bytes)],
      ['Complete log lines read',count(source.lines)],['Access records evaluated',count(source.parsed_records)],
      ['Nginx / Caddy records',count(source.nginx_records)+' / '+count(source.caddy_records)],
      ['Map requests in the scan',count(source.map_requests)],['Health checks excluded',count(source.health_requests||0)],
      ['Other requests excluded',count(other)],['Records after cutoff',count(source.records_after_cutoff||0)],
      ['Partial trailing records deferred',count(source.incomplete_tail_records||0)],['Parse errors',count(source.parse_errors||0)],
      ['Log scan and counter aggregation',secondsLabel(source.scan_seconds)],['Peak calculation and summaries',secondsLabel(source.summarize_seconds)],
      ['Report-file generation',secondsLabel(processing?.render_seconds)],['Total processing time',secondsLabel(processing?.total_seconds)],
      ['Peak reporter process memory',bytesLabel(processing?.peak_process_rss_bytes)],
      ['Scan throughput',source.scan_seconds>0?integer.format((source.parsed_records||0)/source.scan_seconds)+' records/s':'Not recorded'],
      ['Scopes evaluated, including comparisons',count(source.evaluated_scopes)],
      ['Report views generated',count(processing?.reports_generated??report.scopes.length)],['Output buckets across views',count(processing?.output_buckets)],
      ['This view',integer.format(report.rows.length)+' '+report.period.group+' buckets · '+report.period.timezone]
    ];
    get('technical-metrics').replaceChildren();
    for(const [label,value] of rows){const entry=document.createElement('div');entry.className='space-y-1';const name=document.createElement('dt');name.className='muted';name.textContent=label;const detail=document.createElement('dd');detail.className='text-sm tabular-nums';detail.textContent=value;entry.append(name,detail);get('technical-metrics').append(entry);}
    text('technical-preview',integer.format(source.files||0)+' files · '+integer.format(source.parsed_records||0)+' records'+(processing?.total_seconds!=null?' · '+secondsLabel(processing.total_seconds):''));
    text('diagnostics',processing?.cached_render?'Pages were refreshed from cached aggregates. Saved scan metrics refer to the original data-processing run; unmeasured values are marked Not recorded.':processing?.timing_boundary==='published'?'Total processing time includes configuration, scanning, aggregation, report generation, publication and old-output cleanup. Final timing bookkeeping and completion logging add a small overhead.':'Timing currently covers report preparation. Final publication timing is available after completion; reload to refresh it.');
  }
  technicalDetails();
  async function loadProcessing() {
    try {
      const url=new URL('assets/processing.json',window.location.href);
      url.username='';url.password='';
      const response=await window.fetch(url.href,{cache:'no-cache',credentials:'same-origin'});
      if(!response.ok)throw new Error('HTTP '+response.status);
      const value=await response.json();
      if(value.generated_at!==report.generated_at)throw new Error('Generation changed; reload the report');
      processing=value;technicalDetails();
    } catch(error) {
      console.warn('Map report processing metrics could not be loaded:',error.name,error.message);
    }
  }
  loadProcessing();
  reset();
  const initialise=()=>{
    if(!window.echarts){text('filter-note','Chart assets could not be loaded. Use the aggregate JSON download or reload the page.');return;}
    for(const name of Object.keys(titles))charts.set(name,echarts.init(get(name),null,{renderer:'svg'}));
    ready=true;draw();
    new ResizeObserver(()=>{for(const chart of charts.values())chart.resize();}).observe(document.querySelector('main'));
    new ResizeObserver(()=>{if(expanded)expanded.resize();}).observe(get('expanded-chart'));
  };
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',initialise,{once:true});else initialise();
})();
