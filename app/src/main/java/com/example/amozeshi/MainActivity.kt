package com.example.amozeshi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Student(val name:String, val clazz:String, val subject:String, val scores:Map<String,Float>)
data class ScoreColumn(val name:String, val max:Float=20f, val weight:Float=1f)

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { YarMoallemTheme { App() } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App() {
    val ctx= LocalContext.current
    val prefs=ctx.getSharedPreferences("yar_moallem",0)
    var students by remember { mutableStateOf(loadStudents(prefs.getString("students","[]") ?: "[]")) }
    var columns by remember { mutableStateOf(loadColumns(prefs.getString("columns","[\"مستمر\",\"شفاهی\",\"آزمون\"]") ?: "[]")) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedClass by remember { mutableStateOf("نهم ۱") }
    var selectedSubject by remember { mutableStateOf("مطالعات اجتماعی") }
    var nameInput by remember { mutableStateOf("") }
    var classInput by remember { mutableStateOf("نهم ۱") }
    var subjectInput by remember { mutableStateOf("مطالعات اجتماعی") }
    var newColumn by remember { mutableStateOf("") }
    var lessonRequest by remember { mutableStateOf("") }
    var lessonOutput by remember { mutableStateOf("") }
    var speechText by remember { mutableStateOf("") }
    var speechColumn by remember { mutableStateOf("") }
    var snackbar by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key","") ?: "") }
    var editStudent by remember { mutableStateOf<Student?>(null) }
    val scope=rememberCoroutineScope()
    var lessonLoading by remember { mutableStateOf(false) }
    fun persist() { prefs.edit().putString("students",saveStudents(students)).putString("columns",saveColumns(columns)).apply() }
    val speechLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        speechText=spoken
        if(spoken.isNotBlank()) snackbar="گفتار دریافت شد؛ آن را بررسی و ثبت کنید."
    }
    fun startSpeech() {
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT,"نام دانش‌آموز و نمره را بگویید")
        }
        try { speechLauncher.launch(intent) } catch(_:Exception) { snackbar="تشخیص گفتار در این دستگاه در دسترس نیست." }
    }
    Scaffold(
        topBar={ TopAppBar(title={Text("یار معلم",fontWeight=FontWeight.Bold)}, actions={IconButton(onClick={selectedTab=5}){Icon(Icons.Default.Settings,"تنظیمات")}}) },
        bottomBar={ NavigationBar { val labels=listOf("خانه","کلاس","نمرات","تحلیل","طرح درس","تنظیمات"); val icons=listOf(Icons.Default.Home,Icons.Default.Groups,Icons.Default.Assignment,Icons.Default.Insights,Icons.Default.AutoAwesome,Icons.Default.Settings)
            labels.forEachIndexed { i,label -> NavigationBarItem(selected=selectedTab==i,onClick={selectedTab=i},icon={Icon(icons[i],label)},label={Text(label)}) } } },
        snackbarHost={SnackbarHost(remember { SnackbarHostState() }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(selectedTab) {
                0 -> HomeScreen(students,columns,onNavigate={selectedTab=it},selectedClass,selectedSubject)
                1 -> {
                    Text("مدیریت کلاس‌ها",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    OutlinedTextField(nameInput,{nameInput=it},label={Text("نام دانش‌آموز")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(classInput,{classInput=it},label={Text("کلاس / پایه")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(subjectInput,{subjectInput=it},label={Text("درس")},modifier=Modifier.fillMaxWidth())
                    Button(onClick={if(nameInput.isNotBlank()&&classInput.isNotBlank()&&subjectInput.isNotBlank()){
                        val exists=students.any{it.name.equals(nameInput.trim(),true)&&it.clazz==classInput.trim()&&it.subject==subjectInput.trim()}
                        if(!exists){students=students+Student(nameInput.trim(),classInput.trim(),subjectInput.trim(),emptyMap());persist();snackbar="دانش‌آموز ثبت شد."}else snackbar="این دانش‌آموز قبلاً ثبت شده است."
                        nameInput=""
                    }},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PersonAdd,"");Text("ثبت دانش‌آموز")}
                    Text("کلاس‌ها و درس‌ها",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    students.groupBy{it.clazz to it.subject}.forEach{(group,list)->
                        Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text(group.first,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(group.second,color=MaterialTheme.colorScheme.primary)
                            Text(list.size.toString()+" دانش‌آموز · "+list.count{it.scores.isNotEmpty()}+" دارای نمره")
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                OutlinedButton(onClick={selectedClass=group.first;selectedSubject=group.second;selectedTab=2},modifier=Modifier.weight(1f)){Text("دفتر نمرات")}
                                OutlinedButton(onClick={selectedClass=group.first;selectedSubject=group.second;selectedTab=3},modifier=Modifier.weight(1f)){Text("تحلیل")}
                            }
                            list.forEach{st->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(st.name,fontWeight=FontWeight.SemiBold);Text("میانگین: "+(average(st.scores)?.let{"%.2f".format(it)}?: "—"))};IconButton(onClick={students=students.filterNot{it==st};persist()}){Icon(Icons.Default.Delete,"حذف")}}}
                        }}
                    }
                }
                2 -> {
                    Text("دفتر نمرات",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text("انتخاب کلاس و درس؛ ثبت نمرات و مشاهده خلاصه وضعیت.")
                    val classOptions=students.map{it.clazz}.distinct().sorted()
                    var classMenu by remember{mutableStateOf(false)}
                    ExposedDropdownMenuBox(expanded=classMenu,onExpandedChange={classMenu=it}){OutlinedTextField(selectedClass,{selectedClass=it},readOnly=true,label={Text("کلاس")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(classMenu)},modifier=Modifier.fillMaxWidth().menuAnchor());ExposedDropdownMenu(expanded=classMenu,onDismissRequest={classMenu=false}){classOptions.forEach{DropdownMenuItem(text={Text(it)},onClick={selectedClass=it;classMenu=false})}}}
                    val subjectOptions=students.filter{it.clazz==selectedClass}.map{it.subject}.distinct().sorted()
                    var subjectMenu by remember{mutableStateOf(false)}
                    ExposedDropdownMenuBox(expanded=subjectMenu,onExpandedChange={subjectMenu=it}){OutlinedTextField(selectedSubject,{selectedSubject=it},readOnly=true,label={Text("درس")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(subjectMenu)},modifier=Modifier.fillMaxWidth().menuAnchor());ExposedDropdownMenu(expanded=subjectMenu,onDismissRequest={subjectMenu=false}){subjectOptions.forEach{DropdownMenuItem(text={Text(it)},onClick={selectedSubject=it;subjectMenu=false})}}}
                    val roster=students.filter{it.clazz==selectedClass&&it.subject==selectedSubject}.sortedBy{it.name}
                    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("تعداد");Text(roster.size.toString(),fontWeight=FontWeight.Bold)};Column{Text("دارای نمره");Text(roster.count{it.scores.isNotEmpty()}.toString(),fontWeight=FontWeight.Bold)};Column{Text("میانگین کلاس");Text(classAverage(roster)?.let{"%.2f".format(it)}?: "—",fontWeight=FontWeight.Bold)}}}
                    if(columns.isNotEmpty()&&speechColumn.isBlank())speechColumn=columns.first().name
                    var colMenu by remember{mutableStateOf(false)}
                    ExposedDropdownMenuBox(expanded=colMenu,onExpandedChange={colMenu=it}){OutlinedTextField(speechColumn,{speechColumn=it},readOnly=true,label={Text("ستون ثبت صوتی")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(colMenu)},modifier=Modifier.fillMaxWidth().menuAnchor());ExposedDropdownMenu(expanded=colMenu,onDismissRequest={colMenu=false}){columns.forEach{DropdownMenuItem(text={Text(it.name)},onClick={speechColumn=it.name;colMenu=false})}}}
                    Button(onClick={startSpeech()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Mic,"");Text("ثبت نمره با صوت")}
                    if(speechText.isNotBlank())Card{Column(Modifier.padding(12.dp)){Text(speechText);Button(onClick={val parsed=parseVoice(speechText);val target=parsed?.let{p->roster.firstOrNull{it.name.contains(p.first,true)}};if(parsed!=null&&target!=null){students=students.map{if(it==target)it.copy(scores=it.scores+(speechColumn to parsed.second))else it};persist();snackbar="نمره ثبت شد."}else snackbar="نام دانش‌آموز را بررسی کنید.";speechText=""}){Text("تأیید و ثبت")}}}
                    roster.forEach{st->Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(st.name,fontWeight=FontWeight.Bold);Text(st.clazz+" · "+st.subject)};Text(average(st.scores)?.let{"%.2f".format(it)}?: "—",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}
                    columns.forEach{c->var v by remember(st.name,st.clazz,st.subject,c.name){mutableStateOf(st.scores[c.name]?.toString().orEmpty())};OutlinedTextField(v,{v=it;val n=it.replace("٫",".").toFloatOrNull();if(n!=null&&n in 0f..c.max){students=students.map{if(it==st)it.copy(scores=it.scores+(c.name to n))else it};persist()}else if(it.isBlank()){students=students.map{if(it==st)it.copy(scores=it.scores-c.name)else it};persist()}},label={Text(c.name+" (از "+c.max+")")},singleLine=true,modifier=Modifier.fillMaxWidth())}
                    Text(scoreStatus(average(st.scores)),color=MaterialTheme.colorScheme.secondary)
                    }}}
                    Button(onClick={shareGradebook(ctx,selectedClass,selectedSubject,roster,columns)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileDownload,"");Text("خروجی جدول در Word")}
                }
                3 -> {
                    Text("تحلیل عملکرد کلاس",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text("تحلیل بر اساس کلاس و درس انتخاب‌شده؛ شاخص‌ها شفاف و قابل پیگیری‌اند.")
                    val classOptions=students.map{it.clazz}.distinct().sorted()
                    var classMenu by remember{mutableStateOf(false)}
                    ExposedDropdownMenuBox(expanded=classMenu,onExpandedChange={classMenu=it}){OutlinedTextField(selectedClass,{selectedClass=it},readOnly=true,label={Text("کلاس")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(classMenu)},modifier=Modifier.fillMaxWidth().menuAnchor());ExposedDropdownMenu(expanded=classMenu,onDismissRequest={classMenu=false}){classOptions.forEach{DropdownMenuItem(text={Text(it)},onClick={selectedClass=it;classMenu=false})}}}
                    val subjects=students.filter{it.clazz==selectedClass}.map{it.subject}.distinct().sorted()
                    var subjectMenu by remember{mutableStateOf(false)}
                    ExposedDropdownMenuBox(expanded=subjectMenu,onExpandedChange={subjectMenu=it}){OutlinedTextField(selectedSubject,{selectedSubject=it},readOnly=true,label={Text("درس")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(subjectMenu)},modifier=Modifier.fillMaxWidth().menuAnchor());ExposedDropdownMenu(expanded=subjectMenu,onDismissRequest={subjectMenu=false}){subjects.forEach{DropdownMenuItem(text={Text(it)},onClick={selectedSubject=it;subjectMenu=false})}}}
                    val report=students.filter{it.clazz==selectedClass&&it.subject==selectedSubject}.sortedBy{it.name}
                    val avgs=report.mapNotNull{average(it.scores)}
                    val classAvg=if(avgs.isEmpty())null else avgs.average().toFloat()
                    val high=report.count{(average(it.scores)?:-1f)>=17};val low=report.count{val a=average(it.scores);a!=null&&a<12};val missing=report.count{it.scores.isEmpty()}
                    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(selectedClass+" · "+selectedSubject,fontWeight=FontWeight.Bold);Text("میانگین کلاس: "+(classAvg?.let{"%.2f".format(it)}?: "—"),style=MaterialTheme.typography.titleLarge);Text("دانش‌آموزان: "+report.size+" | عملکرد قوی (۱۷+): "+high+" | نیازمند توجه (<۱۲): "+low+" | بدون نمره: "+missing)}}
                    Text("توزیع میانگین",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    listOf("۱۸ تا ۲۰" to report.count{(average(it.scores)?:-1f)>=18},"۱۶ تا ۱۸" to report.count{val a=average(it.scores);a!=null&&a>=16&&a<18},"۱۴ تا ۱۶" to report.count{val a=average(it.scores);a!=null&&a>=14&&a<16},"۱۲ تا ۱۴" to report.count{val a=average(it.scores);a!=null&&a>=12&&a<14},"کمتر از ۱۲" to low,"بدون نمره" to missing).forEach{(label,count)->Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));LinearProgressIndicator(progress={if(report.isEmpty())0f else count.toFloat()/report.size},Modifier.weight(1f));Text(count.toString(),Modifier.width(32.dp))}}
                    Button(onClick={shareAnalysis(ctx,selectedClass,selectedSubject,report,columns)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileDownload,"");Text("خروجی تحلیل در Word")}
                    report.forEach{StudentAnalysisCard(it,columns)}
                }
                4 -> {
                    Text("دستیار هوشمند طرح درس",style=MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(lessonRequest,{lessonRequest=it},label={Text("مثلاً طرح درس درس چهارم تاریخ پایه نهم")},modifier=Modifier.fillMaxWidth(),minLines=2)
                    Button(onClick={
                        if(lessonRequest.isBlank()) { snackbar="عنوان کتاب، پایه، درس یا فصل را وارد کنید."; return@Button }
                        lessonLoading=true
                        lessonOutput=""
                        scope.launch {
                            lessonOutput=if(apiKey.isNotBlank()) generateLessonWithGroq(apiKey,lessonRequest) else "برای جستجوی منابع کتاب و تولید طرح درس هوشمند، کلید API گروک (Groq) را در تنظیمات وارد کنید.\\n\\n"+lessonTemplate(lessonRequest)
                            lessonLoading=false
                        }
                    },modifier=Modifier.fillMaxWidth(),enabled=!lessonLoading){Icon(Icons.Default.AutoAwesome,"");Text(if(lessonLoading)"در حال جستجو و تحلیل کتاب..." else "جستجوی کتاب و تهیه طرح درس")}
                    if(lessonOutput.isNotBlank()) Card { Column(Modifier.padding(12.dp)){Text(lessonOutput);Button(onClick={ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,lessonOutput)},"اشتراک طرح درس"))}){Text("اشتراک‌گذاری")} } }
                    Text("جستجوی منابع و تحلیل داخل برنامه انجام می‌شود؛ هیچ صفحه جستجوی خارجی باز نخواهد شد. اگر متن دقیق کتاب پیدا نشود، این محدودیت اعلام می‌شود.",style=MaterialTheme.typography.bodySmall)suspend fun generateLessonWithGroq(apiKey:String,request:String):String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val url=java.net.URL("https://api.groq.com/openai/v1/chat/completions")
        val conn=url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod="POST";conn.connectTimeout=30000;conn.readTimeout=120000;conn.doOutput=true
        conn.setRequestProperty("Content-Type","application/json")
        conn.setRequestProperty("Authorization","Bearer ${apiKey.trim()}")
        val prompt="""درخواست معلم: $request

وظیفه:
1) ابتدا با ابزار جستجوی وب، کتاب درسی رسمی آموزش‌وپرورش ایران، PDF کتاب، پایگاه‌های رسمی رشد/chap.sch.ir یا منابع معتبر آموزشی را جستجو کن. برای ریاضی، پایه، عنوان فصل/درس، موضوع، مثال‌ها، فعالیت‌ها و تمرین‌های مرتبط کتاب را دقیق بررسی کن.
2) فقط وقتی مشخصات کتاب و محتوای فصل/درس را از منبع قابل اتکا پیدا کردی، آن را به‌عنوان محتوای کتاب معرفی کن. اگر نتوانستی متن دقیق یا منبع قابل اتکا پیدا کنی، صریح بگو «متن دقیق کتاب تأیید نشد» و طرح درس عمومی را از محتوای قطعی کتاب جدا کن؛ هیچ عنوان، شماره صفحه، مثال یا تمرینی را جعل نکن.
3) اگر پایه، رشته، فصل یا درس در درخواست مبهم است، قبل از طرح درس نهایی ابهام را ذکر کن و سؤال روشن‌کننده پیشنهاد بده.
4) پاسخ فارسی و منظم باشد: مشخصات کتاب و درس، منبع/لینک‌های بررسی‌شده (در صورت دسترسی)، اهداف رفتاری، پیش‌نیازها، وسایل، مراحل تدریس با زمان، فعالیت معلم و دانش‌آموز، مثال‌های منطبق با کتاب، ارزشیابی آغازین/تکوینی/پایانی، تکلیف و تفاوت‌گذاری برای دانش‌آموزان ضعیف/قوی.
5) برای درس ریاضی، روند حل مثال‌ها و مفهوم‌سازی را مرحله‌به‌مرحله و متناسب با سطح پایه توضیح بده. منابع را از هم تفکیک و کیفیت اطمینان را اعلام کن."""
        val body=JSONObject()
            .put("model","openai/gpt-oss-120b")
            .put("messages",JSONArray()
                .put(JSONObject().put("role","system").put("content","دستیار تخصصی طراحی آموزشی و پژوهش کتاب‌های درسی ایران. قبل از پاسخ، از browser_search برای یافتن منابع استفاده کن."))
                .put(JSONObject().put("role","user").put("content",prompt)))
            .put("tools",JSONArray().put(JSONObject().put("type","browser_search")))
            .put("tool_choice","required")
            .put("temperature",0.3)
            .put("max_completion_tokens",6000)
        conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
        val code=conn.responseCode
        val stream=if(code in 200..299)conn.inputStream else conn.errorStream
        val raw=stream?.bufferedReader()?.use{it.readText()} ?: ""
        if(code !in 200..299) {
            val err=try{JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()}catch(_:Exception){""}
            when(code){
                401->"کلید API گروک معتبر نیست. کلید را در تنظیمات بررسی کنید."
                403->"دسترسی به جستجوی وب یا مدل Groq مجاز نیست. وضعیت حساب و دسترسی مدل را بررسی کنید."
                429->"محدودیت درخواست Groq اعمال شده است. کمی بعد دوباره تلاش کنید."
                else->"خطای Groq ($code): ${err.ifBlank{"درخواست ناموفق بود."}}"
            }
        } else {
            val json=JSONObject(raw)
            val content=json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            if(content.isBlank()) "پاسخ جستجو خالی بود. لطفاً درخواست را دقیق‌تر بنویسید یا دوباره تلاش کنید." else content
        }
    } catch(e:Exception) {
        "جستجوی کتاب و اتصال به Groq ناموفق بود: ${e.message ?: "خطای نامشخص"}\\n\\n"+lessonTemplate(request)
    }
}
