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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { App() } } }
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
                    Text("مدیریت کلاس و دانش‌آموز",style=MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(nameInput,{nameInput=it},label={Text("نام دانش‌آموز")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(classInput,{classInput=it},label={Text("کلاس")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(subjectInput,{subjectInput=it},label={Text("درس")},modifier=Modifier.fillMaxWidth())
                    Button(onClick={ if(nameInput.isNotBlank()){students=students+Student(nameInput.trim(),classInput.trim(),subjectInput.trim(),emptyMap());nameInput="";persist()} },modifier=Modifier.fillMaxWidth()){Text("افزودن دانش‌آموز")}
                    students.forEach { s -> Card { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.Bold);Text("${s.clazz} · ${s.subject}")};IconButton(onClick={students=students.filterNot{it==s};persist()}){Icon(Icons.Default.Delete,"حذف")} } } }
                }
                2 -> {
                    Text("دفتر نمرات",style=MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(selectedClass,{selectedClass=it},label={Text("کلاس")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(selectedSubject,{selectedSubject=it},label={Text("درس")},modifier=Modifier.fillMaxWidth())
                    if(speechColumn.isBlank() && columns.isNotEmpty()) speechColumn=columns.first().name
                    var columnExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded=columnExpanded,onExpandedChange={columnExpanded=it}) {
                        OutlinedTextField(value=speechColumn,onValueChange={},readOnly=true,label={Text("ستون نمره")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(columnExpanded)},modifier=Modifier.fillMaxWidth().menuAnchor())
                        ExposedDropdownMenu(expanded=columnExpanded,onDismissRequest={columnExpanded=false}) { columns.forEach{col->DropdownMenuItem(text={Text(col.name)},onClick={speechColumn=col.name;columnExpanded=false})} }
                    }
                    Button(onClick={startSpeech()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Mic,"");Text("ثبت نمره با صوت")}
                    if(speechText.isNotBlank()) Card { Column(Modifier.padding(12.dp)){Text("گفتار تشخیص داده شده:");Text(speechText); Button(onClick={val parsed=parseVoice(speechText); if(parsed!=null){students=students.map{if(it.name.contains(parsed.first,true)&&it.clazz==selectedClass&&it.subject==selectedSubject)it.copy(scores=it.scores+(speechColumn to parsed.second))else it};persist();snackbar="نمره ثبت شد (در صورت تطبیق نام)."}else snackbar="نام و نمره را دستی وارد کنید.";speechText=""}){Text("تلاش برای ثبت")}} }
                    students.filter{it.clazz==selectedClass&&it.subject==selectedSubject}.forEach { s -> Card { Column(Modifier.padding(12.dp)){Text(s.name,fontWeight=FontWeight.Bold);columns.forEach { c -> var value by remember(s.name,c.name){mutableStateOf(s.scores[c.name]?.toString().orEmpty())}; OutlinedTextField(value,{value=it;val n=it.toFloatOrNull();if(n!=null){students=students.map{st->if(st==s)st.copy(scores=st.scores+(c.name to n))else st};persist()}},label={Text("${c.name} (از ${c.max})")},modifier=Modifier.fillMaxWidth())};Text("میانگین: ${average(s.scores).let{if(it==null)"—" else "%.2f".format(it)}}") } } }
                }
                3 -> {
                    Text("تحلیل دانش‌آموزان",style=MaterialTheme.typography.headlineSmall)
                    students.forEach { s -> StudentAnalysisCard(s,columns) }
                }
                4 -> {
                    Text("دستیار هوشمند طرح درس",style=MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(lessonRequest,{lessonRequest=it},label={Text("مثلاً طرح درس درس چهارم تاریخ پایه نهم")},modifier=Modifier.fillMaxWidth(),minLines=2)
                    Button(onClick={
                        lessonLoading=true
                        scope.launch {
                            lessonOutput=if(apiKey.isNotBlank()) generateLessonWithGemini(apiKey,lessonRequest) else lessonTemplate(lessonRequest)+"\n\nبرای تولید طرح درس مبتنی بر جستجوی وب، کلید Gemini API را در تنظیمات وارد کنید."
                            lessonLoading=false
                        }
                        val q=java.net.URLEncoder.encode(lessonRequest+" کتاب درسی طرح درس نکات مهم","UTF-8")
                        ctx.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.google.com/search?q=$q")))
                    },modifier=Modifier.fillMaxWidth(),enabled=!lessonLoading){Icon(Icons.Default.Search,"");Text(if(lessonLoading)"در حال تهیه طرح درس..." else "جستجوی منابع و تهیه طرح درس")}
                    if(lessonOutput.isNotBlank()) Card { Column(Modifier.padding(12.dp)){Text(lessonOutput);Button(onClick={ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,lessonOutput)},"اشتراک طرح درس"))}){Text("اشتراک‌گذاری")} } }
                    Text("با کلید Gemini API، طرح درس به‌صورت خودکار تولید می‌شود و ابزار جستجوی وب مدل برای یافتن منابع فعال می‌شود. مرورگر نیز برای جستجوی تکمیلی باز خواهد شد.",style=MaterialTheme.typography.bodySmall)
                }
                5 -> {
                    Text("تنظیمات",style=MaterialTheme.typography.headlineSmall)
                    Text("طراحی ستون‌های دفتر نمرات")
                    columns.forEach { c -> Row(verticalAlignment=Alignment.CenterVertically){Text(c.name,Modifier.weight(1f));IconButton(onClick={columns=columns.filterNot{it==c};persist()}){Icon(Icons.Default.Delete,"حذف ستون")} } }
                    OutlinedTextField(newColumn,{newColumn=it},label={Text("نام ستون جدید")},modifier=Modifier.fillMaxWidth())
                    Button(onClick={if(newColumn.isNotBlank()){columns=columns+ScoreColumn(newColumn);newColumn="";persist()}},modifier=Modifier.fillMaxWidth()){Text("افزودن ستون")}
                    OutlinedTextField(apiKey,{apiKey=it;prefs.edit().putString("api_key",it).apply()},label={Text("کلید API هوش مصنوعی (اختیاری)")},modifier=Modifier.fillMaxWidth())
                    Text("اطلاعات دانش‌آموزان روی همین دستگاه ذخیره می‌شود. برای پشتیبان‌گیری و همگام‌سازی در نسخه‌های بعدی.")
                }
            }
            if(snackbar.isNotBlank()) Text(snackbar,color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable fun HomeScreen(students:List<Student>,columns:List<ScoreColumn>,onNavigate:(Int)->Unit,clazz:String,subject:String){
    Text("دستیار آموزشی معلم",style=MaterialTheme.typography.headlineSmall)
    Card { Column(Modifier.padding(16.dp)){Text("خوش آمدید",fontWeight=FontWeight.Bold);Text("کلاس فعال: $clazz");Text("درس: $subject");Text("دانش‌آموزان ثبت‌شده: ${students.size}")} }
    val items=listOf("مدیریت کلاس و دانش‌آموزان" to 1,"دفتر نمرات" to 2,"تحلیل عملکرد دانش‌آموزان" to 3,"دستیار طرح درس و جستجو" to 4,"طراحی جدول و تنظیمات" to 5)
    items.forEach{(t,i)->Card(onClick={onNavigate(i)}){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(t,Modifier.weight(1f));Icon(Icons.Default.ChevronRight,"")}}}
}
@Composable fun StudentAnalysisCard(s:Student,columns:List<ScoreColumn>){
    val vals=s.scores.values
    val avg=average(s.scores)
    val missing=columns.filter{it.name !in s.scores}.map{it.name}
    val low=s.scores.filterValues{it<12}.keys
    val highest=s.scores.maxByOrNull{it.value}
    Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        Text(s.name,fontWeight=FontWeight.Bold);Text("${s.clazz} · ${s.subject}")
        Text("میانگین ثبت‌شده: ${avg?.let{"%.2f".format(it)} ?: "بدون نمره"}")
        Text("تعداد نمرات: ${vals.count()}")
        Text("بالاترین نمره: ${highest?.let{"${it.key}: ${it.value}"} ?: "—"}")
        Text("نیاز به توجه: ${if(low.isEmpty())"موردی بر اساس آستانه ۱۲ شناسایی نشد" else low.joinToString()}")
        Text("نمرات ثبت‌نشده: ${if(missing.isEmpty())"ندارد" else missing.joinToString()}")
        if(avg!=null) Text(when{avg>=17->"عملکرد بسیار خوب؛ پیشنهاد: فعالیت‌های چالشی و تعمیقی.";avg>=14->"عملکرد خوب؛ مرور هدفمند نکات دشوار.";avg>=12->"عملکرد متوسط؛ تمرین تکمیلی توصیه می‌شود.";else->"نیازمند برنامه حمایتی، بررسی نقاط ضعف و پیگیری فردی."})
    } }
}
fun average(scores:Map<String,Float>):Float?=scores.values.takeIf{it.isNotEmpty()}?.average()?.toFloat()
fun parseVoice(text:String):Pair<String,Float>?{
    val nums=Regex("""\d+(?:[٫.]\d+)?""").findAll(text).mapNotNull{it.value.replace('٫','.').toFloatOrNull()}.toList()
    if(nums.isEmpty())return null
    val score=nums.last()
    val name=text.replace(Regex("""\d+(?:[٫.]\d+)?"""),"").replace(Regex("""(نمره|امتحان|آزمون|درس|از|برای)"""),"").trim()
    return name.takeIf{it.isNotBlank()}?.let{it to score}
}
suspend fun generateLessonWithGemini(apiKey:String,request:String):String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val url=java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key="+java.net.URLEncoder.encode(apiKey,"UTF-8"))
        val conn=url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod="POST";conn.connectTimeout=30000;conn.readTimeout=60000;conn.doOutput=true
        conn.setRequestProperty("Content-Type","application/json")
        val prompt="تو دستیار حرفه‌ای معلم فارسی‌زبان هستی. درخواست معلم: $request. اگر پایه یا عنوان درس مبهم/ناسازگار است، ابتدا این ابهام را در پاسخ مشخص کن. طرح درس شامل مشخصات، اهداف کلی و رفتاری، پیش‌نیاز، وسایل، مراحل تدریس و زمان، فعالیت معلم و دانش‌آموز، پرسش‌های ارزشیابی، تکلیف، نکات مهم کتاب و منابع باشد. مطالب factual را با جستجوی وب بررسی کن و منابع را ذکر کن. پاسخ فارسی و ساختاریافته باشد."
        val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt)))))
            .put("tools",JSONArray().put(JSONObject().put("google_search",JSONObject())))
        conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
        val stream=if(conn.responseCode in 200..299)conn.inputStream else conn.errorStream
        val raw=stream.bufferedReader().use{it.readText()}
        if(conn.responseCode !in 200..299) "خطای سرویس هوش مصنوعی: $raw" else {
            val json=JSONObject(raw);json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
    } catch(e:Exception) { "اتصال به هوش مصنوعی ناموفق بود: ${e.message}\n\n"+lessonTemplate(request) }
}
fun lessonTemplate(req:String)= """طرح درس پیشنهادی
درخواست: $req

۱. اهداف آموزشی
• آشنایی با مفاهیم اصلی درس
• توانایی توضیح و تحلیل مطالب
• مشارکت فعال در بحث

۲. شروع کلاس (۵ دقیقه)
• پرسش از آموخته‌های قبلی
• ایجاد انگیزه با سؤال مرتبط

۳. تدریس (۲۰ تا ۳۰ دقیقه)
• ارائه مفاهیم کلیدی
• مثال و ارتباط با زندگی روزمره
• گفت‌وگوی هدایت‌شده

۴. فعالیت دانش‌آموزان
• کار گروهی و پاسخ به پرسش‌ها
• خلاصه‌سازی نکات مهم

۵. ارزشیابی
• پرسش شفاهی
• چند سؤال کوتاه
• بررسی مشارکت

۶. تکلیف
• خلاصه درس
• پاسخ به پرسش‌های منتخب کتاب

توجه: برای طرح درس دقیق، متن کتاب یا منابع معتبر را بررسی و نکات را تکمیل کنید.
""".trimIndent()
fun loadStudents(raw:String):List<Student> = try { val a=JSONArray(raw);(0 until a.length()).map{val o=a.getJSONObject(it);val sc=o.getJSONObject("scores");val m=mutableMapOf<String,Float>();sc.keys().forEach{m[it]=sc.getDouble(it).toFloat()};Student(o.getString("name"),o.getString("clazz"),o.getString("subject"),m)} }catch(_:Exception){emptyList()}
fun saveStudents(list:List<Student>):String {val a=JSONArray();list.forEach{s->val scores=JSONObject();s.scores.forEach{(k,v)->scores.put(k,v)};a.put(JSONObject().put("name",s.name).put("clazz",s.clazz).put("subject",s.subject).put("scores",scores))};return a.toString()}
fun loadColumns(raw:String):List<ScoreColumn> = try {val a=JSONArray(raw);(0 until a.length()).map{ScoreColumn(a.getString(it))}}catch(_:Exception){listOf(ScoreColumn("مستمر"),ScoreColumn("شفاهی"),ScoreColumn("آزمون"))}
fun saveColumns(list:List<ScoreColumn>)=JSONArray().also{a->list.forEach{a.put(it.name)}}.toString()
