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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { YarMoallemTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App() } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App() {
    val ctx= LocalContext.current
    val prefs=ctx.getSharedPreferences("yar_moallem",0)
    var students by remember { mutableStateOf(loadStudents(prefs.getString("students","[]") ?: "[]")) }
    var columns by remember { mutableStateOf(loadColumns(prefs.getString("columns","[\"مستمر\",\"شفاهی\",\"آزمون\"]") ?: "[]")) }
    var selectedTab by remember { mutableStateOf(0) }
    var settingsPage by remember { mutableStateOf("") }
    var attendanceDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var attendance by remember { mutableStateOf(JSONObject(prefs.getString("attendance","{}") ?: "{}")) }
    var timetable by remember { mutableStateOf(prefs.getString("timetable","") ?: "") }
    var timetableEdit by remember { mutableStateOf(timetable) }
    var selectedClass by remember { mutableStateOf("نهم ۱") }
    var selectedSubject by remember { mutableStateOf("مطالعات اجتماعی") }
    var nameInput by remember { mutableStateOf("") }
    var classInput by remember { mutableStateOf("نهم ۱") }
    var subjectInput by remember { mutableStateOf("مطالعات اجتماعی") }
    var newColumn by remember { mutableStateOf("") }
    var lessonRequest by remember { mutableStateOf("") }
    var lessonOutput by remember { mutableStateOf("") }
    var quizConfigOpen by remember { mutableStateOf(false) }
    var quizTypes by remember { mutableStateOf(setOf("چهارگزینه‌ای","تشریحی")) }
    var quizCounts by remember { mutableStateOf(mapOf("چهارگزینه‌ای" to "5","تشریحی" to "3","صحیح و غلط" to "0","جای خالی" to "0")) }
    var quizDifficulty by remember { mutableStateOf("ترکیبی") }
    var quizAnswerKey by remember { mutableStateOf(true) }
    var speechText by remember { mutableStateOf("") }
    var speechColumn by remember { mutableStateOf("") }
    var snackbar by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key","") ?: "") }
    var editStudent by remember { mutableStateOf<Student?>(null) }
    val scope=rememberCoroutineScope()
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) try { ctx.contentResolver.openOutputStream(uri)?.use { it.write(makeBackup(students,columns,attendance,timetable,apiKey).toByteArray(Charsets.UTF_8)) }; snackbar="فایل پشتیبان ذخیره شد." } catch(e:Exception){snackbar="ذخیره پشتیبان ناموفق بود: ${e.message}"}
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) try {
            val raw=ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()} ?: error("فایل قابل خواندن نیست")
            val root=JSONObject(raw)
            require(root.optString("app")=="YarMoallem") {"فایل پشتیبان یار معلم نیست"}
            val newStudents=loadStudents(root.getJSONArray("students").toString())
            val newColumns=loadColumns(root.getJSONArray("columns").toString())
            val newAttendance=root.optJSONObject("attendance") ?: JSONObject()
            val newTimetable=root.optString("timetable","")
            students=newStudents;columns=newColumns;attendance=newAttendance;timetable=newTimetable;timetableEdit=newTimetable;apiKey=root.optString("apiKey",apiKey)
            prefs.edit().putString("students",saveStudents(newStudents)).putString("columns",saveColumns(newColumns)).putString("attendance",newAttendance.toString()).putString("timetable",newTimetable).putString("api_key",apiKey).apply()
            autoBackup(prefs,newStudents,newColumns,newAttendance,newTimetable)
            snackbar="بازیابی کامل شد؛ ${newStudents.size} دانش‌آموز بازیابی شد."
        } catch(e:Exception){snackbar="بازیابی انجام نشد؛ اطلاعات قبلی حفظ شد. ${e.message}"}
    }
    var lessonLoading by remember { mutableStateOf(false) }
    fun persist(studentData:List<Student> = students, columnData:List<ScoreColumn> = columns) { prefs.edit().putString("students",saveStudents(studentData)).putString("columns",saveColumns(columnData)).apply(); autoBackup(prefs,studentData,columnData,attendance,timetable) }
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
        bottomBar={ NavigationBar { val labels=listOf("خانه","کلاس","نمرات","تحلیل","طرح درس","تنظیمات","حضور","برنامه"); val icons=listOf(Icons.Default.Home,Icons.Default.Groups,Icons.Default.Assignment,Icons.Default.Insights,Icons.Default.AutoAwesome,Icons.Default.Settings,Icons.Default.CheckCircle,Icons.Default.CalendarMonth)
            labels.forEachIndexed { i,label -> NavigationBarItem(selected=selectedTab==i,onClick={selectedTab=i},icon={Icon(icons[i],label)},label={Text(label)}) } } },
        snackbarHost={SnackbarHost(remember { SnackbarHostState() }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(selectedTab) {
                0 -> HomeScreen(students,columns,onNavigate={selectedTab=it},selectedClass,selectedSubject,onSelectClass={selectedClass=it;selectedSubject=students.filter{st->st.clazz==it}.map{st->st.subject}.distinct().firstOrNull() ?: selectedSubject})
                1 -> {
                    Text("مدیریت کلاس‌ها", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
                    OutlinedTextField(nameInput, { nameInput=it }, label={Text("نام دانش‌آموز")}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(classInput, { classInput=it }, label={Text("کلاس / پایه")}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(subjectInput, { subjectInput=it }, label={Text("درس")}, modifier=Modifier.fillMaxWidth())
                    Button(onClick={
                        if(nameInput.isNotBlank() && classInput.isNotBlank() && subjectInput.isNotBlank()) {
                            val exists=students.any{it.name.equals(nameInput.trim(),true)&&it.clazz==classInput.trim()&&it.subject==subjectInput.trim()}
                            if(!exists) {
                                students=students+Student(nameInput.trim(),classInput.trim(),subjectInput.trim(),emptyMap())
                                persist()
                                snackbar="دانش‌آموز ثبت شد."
                            } else snackbar="این دانش‌آموز قبلاً ثبت شده است."
                            nameInput=""
                        }
                    }, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.PersonAdd,""); Text("ثبت دانش‌آموز") }
                    Text("کلاس‌ها و درس‌ها", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                    students.groupBy{it.clazz to it.subject}.forEach{(group,list)->
                        Card(modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(group.first, fontWeight=FontWeight.Bold, style=MaterialTheme.typography.titleMedium)
                                Text(group.second, color=MaterialTheme.colorScheme.primary)
                                Text("${list.size} دانش‌آموز · ${list.count{it.scores.isNotEmpty()}} دارای نمره")
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick={selectedClass=group.first;selectedSubject=group.second;selectedTab=2}, modifier=Modifier.weight(1f)){Text("دفتر نمرات")}
                                    OutlinedButton(onClick={selectedClass=group.first;selectedSubject=group.second;selectedTab=3}, modifier=Modifier.weight(1f)){Text("تحلیل")}
                                }
                                list.forEach{st->
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(st.name,fontWeight=FontWeight.SemiBold)
                                            Text("میانگین: "+(average(st.scores)?.let{"%.2f".format(it)}?: "—"))
                                        }
                                        IconButton(onClick={students=students.filterNot{it==st};persist()}){Icon(Icons.Default.Delete,"حذف")}
                                    }
                                }
                            }
                        }
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
                    if(speechText.isNotBlank())Card{Column(Modifier.padding(12.dp)){Text(speechText);Button(onClick={val parsed=parseVoice(speechText);val target=parsed?.let{p->roster.firstOrNull{it.name.contains(p.first,true)}};if(parsed!=null&&target!=null){val updated=students.map{if(it==target)it.copy(scores=it.scores+(speechColumn to parsed.second))else it};students=updated;persist(updated);snackbar="نمره ثبت شد."}else snackbar="نام دانش‌آموز را بررسی کنید.";speechText=""}){Text("تأیید و ثبت")}}}
                    roster.forEach{st->Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(st.name,fontWeight=FontWeight.Bold);Text(st.clazz+" · "+st.subject)};Text(average(st.scores)?.let{"%.2f".format(it)}?: "—",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}
                    columns.forEach{c->val currentScore=st.scores[c.name];var v by remember(st.name,st.clazz,st.subject,c.name,currentScore){mutableStateOf(currentScore?.toString().orEmpty())};OutlinedTextField(value=v,onValueChange={input->v=input;val normalized=normalizeDigits(input).replace("٫",".").replace(",",".");val n=normalized.toFloatOrNull();if(n!=null&&n in 0f..c.max){val updated=students.map{row->if(row.name==st.name&&row.clazz==st.clazz&&row.subject==st.subject)row.copy(scores=row.scores+(c.name to n))else row};students=updated;persist(updated)}else if(input.isBlank()){val updated=students.map{row->if(row.name==st.name&&row.clazz==st.clazz&&row.subject==st.subject)row.copy(scores=row.scores-c.name)else row};students=updated;persist(updated)}},label={Text(c.name+" (از "+c.max+")")},singleLine=true,modifier=Modifier.fillMaxWidth())}
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
                    val scoredCount=report.count{it.scores.isNotEmpty()}
                    val columnAnalysis=columns.map{col->val vals=report.mapNotNull{it.scores[col.name]};col to (if(vals.isEmpty())null else vals.average().toFloat())}
                    val bestStudents=report.mapNotNull{st->average(st.scores)?.let{st to it}}.sortedByDescending{it.second}.take(3)
                    val high=report.count{(average(it.scores)?:-1f)>=17};val low=report.count{val a=average(it.scores);a!=null&&a<12};val missing=report.count{it.scores.isEmpty()}
                    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(selectedClass+" · "+selectedSubject,fontWeight=FontWeight.Bold);Text("میانگین کلاس: "+(classAvg?.let{"%.2f".format(it)}?: "—"),style=MaterialTheme.typography.titleLarge);Text("دانش‌آموزان: "+report.size+" | دارای نمره: "+scoredCount+" | عملکرد قوی (۱۷+): "+high+" | نیازمند توجه (<۱۲): "+low+" | بدون نمره: "+missing);Text("میانگین فقط از نمرات ثبت‌شده محاسبه می‌شود.",style=MaterialTheme.typography.bodySmall)}}
                    Text("میانگین هر ستون",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);columnAnalysis.forEach{(col,avg)->Card{Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(col.name);Text(avg?.let{"میانگین %.2f".format(it)}?: "بدون نمره",fontWeight=FontWeight.Bold)}}};if(bestStudents.isNotEmpty()){Text("۳ دانش‌آموز برتر",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);bestStudents.forEach{(st,avg)->Text("${st.name} — %.2f".format(avg))}};Text("توزیع میانگین",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    listOf("۱۸ تا ۲۰" to report.count{(average(it.scores)?:-1f)>=18},"۱۶ تا ۱۸" to report.count{val a=average(it.scores);a!=null&&a>=16&&a<18},"۱۴ تا ۱۶" to report.count{val a=average(it.scores);a!=null&&a>=14&&a<16},"۱۲ تا ۱۴" to report.count{val a=average(it.scores);a!=null&&a>=12&&a<14},"کمتر از ۱۲" to low,"بدون نمره" to missing).forEach{(label,count)->Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));LinearProgressIndicator(progress={if(report.isEmpty())0f else count.toFloat()/report.size},Modifier.weight(1f));Text(count.toString(),Modifier.width(32.dp))}}
                    Button(onClick={shareAnalysis(ctx,selectedClass,selectedSubject,report,columns)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileDownload,"");Text("خروجی تحلیل در Word")}
                    report.forEach{StudentAnalysisCard(it,columns)}
                }
                4 -> {
                    Text("دستیار آموزشی هوشمند",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text("طرح درس و آزمون متناسب با کتاب و پایه",style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(lessonRequest,{lessonRequest=it},label={Text("کتاب، پایه، فصل یا موضوع")},modifier=Modifier.fillMaxWidth(),minLines=2)
                    if(quizConfigOpen) {
                    Text("تنظیمات آزمون",fontWeight=FontWeight.Bold)
                    Text("نوع سؤال (چند انتخاب)",style=MaterialTheme.typography.bodyMedium)
                    listOf("چهارگزینه‌ای","تشریحی","صحیح و غلط","جای خالی").forEach { type -> FilterChip(selected=type in quizTypes,onClick={quizTypes=if(type in quizTypes)quizTypes-type else quizTypes+type},label={Text(type)}) }
                    quizTypes.forEach { type -> OutlinedTextField(value=quizCounts[type]?: "1",onValueChange={v->quizCounts=quizCounts+(type to normalizeDigits(v).filter{it.isDigit()}.take(2))},label={Text("تعداد سؤال $type")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
                    Text("درجه سختی",fontWeight=FontWeight.Bold)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("آسان","متوسط","دشوار","ترکیبی").forEach{d->FilterChip(selected=quizDifficulty==d,onClick={quizDifficulty=d},label={Text(d)})}}
                    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=quizAnswerKey,onCheckedChange={quizAnswerKey=it});Text("پاسخنامه و بارم‌بندی")}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Button(onClick={
                            if(lessonRequest.isBlank()){snackbar="موضوع یا مشخصات کتاب را وارد کنید.";return@Button}
                            lessonLoading=true;lessonOutput=""
                            scope.launch{lessonOutput=if(apiKey.isNotBlank())generateLessonWithGroq(apiKey,lessonRequest,"lesson") else lessonTemplate(lessonRequest)+"\\n\\nبرای استفاده از جستجوی کتاب و هوش مصنوعی، کلید Groq را در تنظیمات وارد کنید.";lessonLoading=false}
                        },modifier=Modifier.weight(1f),enabled=!lessonLoading){Icon(Icons.Default.MenuBook,"");Text("طرح درس")}
                        OutlinedButton(onClick={quizConfigOpen=!quizConfigOpen;lessonOutput=""},modifier=Modifier.weight(1f),enabled=!lessonLoading){Icon(Icons.Default.Assignment,"");Text(if(quizConfigOpen)"بستن تنظیمات" else "آزمون‌ساز")}
                    }
                    if(quizConfigOpen) Button(onClick={
                        if(lessonRequest.isBlank()){snackbar="موضوع آزمون را وارد کنید.";return@Button}
                        if(quizTypes.isEmpty()){snackbar="حداقل یک نوع سؤال انتخاب کنید.";return@Button}
                        val counts=quizTypes.map{it to (quizCounts[it]?.toIntOrNull()?:0)}
                        if(counts.any{it.second<1}){snackbar="برای هر نوع سؤال انتخاب‌شده، تعداد معتبر وارد کنید.";return@Button}
                        lessonLoading=true;lessonOutput="";quizConfigOpen=false
                        scope.launch{val config="\nتنظیمات آزمون: نوع سؤال="+quizTypes.joinToString("، ")+"؛ تعداد="+counts.joinToString("، "){it.first+" "+it.second}+"؛ دشواری="+quizDifficulty+"؛ پاسخنامه="+if(quizAnswerKey)"همراه آزمون" else "بدون پاسخنامه";lessonOutput=if(apiKey.isNotBlank())generateLessonWithGroq(apiKey,lessonRequest+config,"quiz") else "برای آزمون‌سازی هوشمند، کلید Groq را در تنظیمات وارد کنید.";lessonLoading=false}
                    },modifier=Modifier.fillMaxWidth(),enabled=!lessonLoading){Text("شروع ساخت آزمون")}
                    if(lessonLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (lessonOutput.isNotBlank()) {
                        Card {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(lessonOutput)
                                Button(
                                    onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, lessonOutput)
                                        }
                                        ctx.startActivity(Intent.createChooser(send, "اشتراک خروجی آموزشی"))
                                    }
                                ) {
                                    Icon(Icons.Default.Share, "")
                                    Text("اشتراک‌گذاری / چاپ")
                                }
                            }
                        }
                    }
                    Text("جستجو و تحلیل داخل برنامه انجام می‌شود؛ مرورگر گوشی باز نمی‌شود. در صورت پیدا نشدن متن رسمی کتاب، خروجی این موضوع را شفاف اعلام می‌کند.",style=MaterialTheme.typography.bodySmall)
                }
                5 -> {
                    Text("تنظیمات",style=MaterialTheme.typography.headlineSmall)
                    if(settingsPage.isBlank()) {
                        listOf("دفتر نمرات","هوش مصنوعی","پشتیبان‌گیری").forEach { page ->
                            Card(onClick={settingsPage=page},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)){Text(page,fontWeight=FontWeight.Bold);Text(when(page){"دفتر نمرات"->"مدیریت ستون‌ها و مقیاس نمرات";"هوش مصنوعی"->"کلید API و تنظیمات تولید محتوا";else->"خروجی و نگهداری داده‌ها"})} }
                        }
                    } else {
                        TextButton(onClick={settingsPage=""}){Text("← بازگشت به تنظیمات")}
                        Text(settingsPage,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        when(settingsPage) {
                            "دفتر نمرات" -> {
                                columns.forEach { c -> Row(verticalAlignment=Alignment.CenterVertically){Text(c.name,Modifier.weight(1f));IconButton(onClick={columns=columns.filterNot{it==c};persist()}){Icon(Icons.Default.Delete,"حذف ستون")} } }
                                OutlinedTextField(newColumn,{newColumn=it},label={Text("نام ستون جدید")},modifier=Modifier.fillMaxWidth())
                                Button(onClick={if(newColumn.isNotBlank()&&columns.none{it.name==newColumn.trim()}){columns=columns+ScoreColumn(newColumn.trim());newColumn="";persist()}},modifier=Modifier.fillMaxWidth()){Text("افزودن ستون")}
                            }
                            "هوش مصنوعی" -> OutlinedTextField(apiKey,{apiKey=it;prefs.edit().putString("api_key",it).apply()},label={Text("کلید API Groq")},modifier=Modifier.fillMaxWidth())
                            "پشتیبان‌گیری" -> {
                                Button(onClick={exportLauncher.launch("YarMoallem_Backup_${java.time.LocalDate.now()}.json")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Backup,"");Text("ذخیره فایل پشتیبان")}
                                Button(onClick={importLauncher.launch(arrayOf("application/json","text/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Restore,"");Text("بازیابی از فایل پشتیبان")}
                                Text("پشتیبان شامل دانش‌آموزان، تمام نمرات، ستون‌های نمره، حضور و غیاب و برنامه هفتگی است. هنگام بازیابی، اطلاعات فعلی با فایل انتخابی جایگزین می‌شود.")
                                Text("پشتیبان خودکار داخلی نیز پس از تغییرات مهم ذخیره می‌شود.",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("اطلاعات دانش‌آموزان روی همین دستگاه ذخیره می‌شود.")
                }
            6 -> {
                Text("حضور و غیاب",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                val attendanceClasses=students.map{it.clazz}.distinct().sorted()
                var attendanceClassMenu by remember{mutableStateOf(false)}
                ExposedDropdownMenuBox(expanded=attendanceClassMenu,onExpandedChange={attendanceClassMenu=it}) {
                    OutlinedTextField(selectedClass,{selectedClass=it},readOnly=true,label={Text("انتخاب کلاس")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(attendanceClassMenu)},modifier=Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded=attendanceClassMenu,onDismissRequest={attendanceClassMenu=false}) { attendanceClasses.forEach{cl->DropdownMenuItem(text={Text(cl)},onClick={selectedClass=cl;attendanceClassMenu=false;selectedSubject=students.filter{it.clazz==cl}.map{it.subject}.distinct().firstOrNull() ?: selectedSubject})} }
                }
                val attendanceSubjects=students.filter{it.clazz==selectedClass}.map{it.subject}.distinct().sorted()
                var attendanceSubjectMenu by remember{mutableStateOf(false)}
                ExposedDropdownMenuBox(expanded=attendanceSubjectMenu,onExpandedChange={attendanceSubjectMenu=it}) {
                    OutlinedTextField(selectedSubject,{selectedSubject=it},readOnly=true,label={Text("درس")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(attendanceSubjectMenu)},modifier=Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded=attendanceSubjectMenu,onDismissRequest={attendanceSubjectMenu=false}) { attendanceSubjects.forEach{su->DropdownMenuItem(text={Text(su)},onClick={selectedSubject=su;attendanceSubjectMenu=false})} }
                }
                var jalaliYear by remember{mutableStateOf(jalaliDateLabel(attendanceDate).take(4))}
                var jalaliMonth by remember{mutableStateOf(jalaliDateLabel(attendanceDate).drop(5).take(2))}
                var jalaliDay by remember{mutableStateOf(jalaliDateLabel(attendanceDate).takeLast(2))}
                Text("تاریخ حضور و غیاب (شمسی)",fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(jalaliYear,{jalaliYear=it;jalaliToIso(it,jalaliMonth,jalaliDay)?.let{attendanceDate=it}},label={Text("سال")},modifier=Modifier.weight(1f),singleLine=true)
                    OutlinedTextField(jalaliMonth,{jalaliMonth=it;jalaliToIso(jalaliYear,it,jalaliDay)?.let{attendanceDate=it}},label={Text("ماه")},modifier=Modifier.weight(1f),singleLine=true)
                    OutlinedTextField(jalaliDay,{jalaliDay=it;jalaliToIso(jalaliYear,jalaliMonth,it)?.let{attendanceDate=it}},label={Text("روز")},modifier=Modifier.weight(1f),singleLine=true)
                }
                Text("تاریخ انتخاب‌شده: "+jalaliDateLabel(attendanceDate),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
                val roster=students.filter{it.clazz==selectedClass&&it.subject==selectedSubject}.sortedBy{it.name}
                Text("کلاس: $selectedClass · درس: $selectedSubject")
                val day=attendance.optJSONObject(attendanceDate)?:JSONObject()
                Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("حاضر: "+roster.count{day.optString(it.name)=="present"});Text("غایب: "+roster.count{day.optString(it.name)=="absent"});Text("ثبت‌نشده: "+roster.count{day.optString(it.name).isBlank()})}}
                Text("سوابق ماه انتخاب‌شده",fontWeight=FontWeight.Bold)
                val monthRecords=attendance.keys().asSequence().filter{it.startsWith(attendanceDate.take(7))}.sorted().toList()
                if(monthRecords.isEmpty()) Text("در این ماه سابقه‌ای ثبت نشده است.") else monthRecords.forEach{dateKey->val rec=attendance.optJSONObject(dateKey)?:JSONObject();Card{Column(Modifier.padding(10.dp)){Text(jalaliDateLabel(dateKey),fontWeight=FontWeight.Bold);Text("حاضر: "+rec.keys().asSequence().count{rec.optString(it)=="present"}+" · غایب: "+rec.keys().asSequence().count{rec.optString(it)=="absent"})}}}
                roster.forEach{st->Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){Text(st.name,Modifier.weight(1f));FilterChip(selected=day.optString(st.name)=="present",onClick={day.put(st.name,"present");attendance.put(attendanceDate,day);prefs.edit().putString("attendance",attendance.toString()).apply();autoBackup(prefs,students,columns,attendance,timetable)},label={Text("حاضر")});Spacer(Modifier.width(6.dp));FilterChip(selected=day.optString(st.name)=="absent",onClick={day.put(st.name,"absent");attendance.put(attendanceDate,day);prefs.edit().putString("attendance",attendance.toString()).apply()},label={Text("غایب")})}}
                Button(
                    onClick = {
                        val report = roster.joinToString("\n") { st ->
                            st.name + " : " + when (day.optString(st.name)) {
                                "present" -> "حاضر"
                                "absent" -> "غایب"
                                else -> "ثبت نشده"
                            }
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "حضور و غیاب $attendanceDate")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        ctx.startActivity(Intent.createChooser(sendIntent, "اشتراک گزارش حضور"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("خروجی حضور و غیاب")
                }
            }
            7 -> {
                Text("برنامه هفتگی",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("هر ردیف را به شکل روز | ساعت | کلاس | درس وارد کنید. جدول پیش‌نمایش خودکار ساخته می‌شود.")
                OutlinedTextField(timetableEdit,{timetableEdit=it},modifier=Modifier.fillMaxWidth(),minLines=6,label={Text("شنبه | ۸ تا ۹ | نهم ۱ | مطالعات")})
                val timetableRows=timetableEdit.lines().mapNotNull{line->val p=line.split("|").map{it.trim()};if(p.size>=4&&p[0].isNotBlank())p else null}
                if(timetableRows.isNotEmpty()) {
                    Text("پیش‌نمایش جدول",fontWeight=FontWeight.Bold)
                    timetableRows.forEach { row -> Card { Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){row.take(4).forEach{Text(it,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)}} } }
                }
                Button(onClick={timetable=timetableEdit;prefs.edit().putString("timetable",timetable).apply();autoBackup(prefs,students,columns,attendance,timetable);snackbar="برنامه هفتگی ذخیره شد."},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Save,"");Text("ذخیره برنامه")}
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, timetable)
                        }
                        ctx.startActivity(Intent.createChooser(sendIntent, "اشتراک برنامه هفتگی"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اشتراک برنامه")
                }
            }
            }
            if(snackbar.isNotBlank()) Text(snackbar,color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable fun HomeScreen(students:List<Student>,columns:List<ScoreColumn>,onNavigate:(Int)->Unit,clazz:String,subject:String,onSelectClass:(String)->Unit){
    val classes=students.groupBy{it.clazz}.toSortedMap()
    Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text("سلام، معلم عزیز 👋",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("کلاس‌های شما",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        if(classes.isEmpty()) {
            Card { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("هنوز کلاسی ثبت نشده است.")
                Button(onClick={onNavigate(1)}){Text("افزودن دانش‌آموز و کلاس")}
            } }
        }
        classes.forEach{(className,roster)->
            val subjects=roster.map{it.subject}.distinct()
            Card(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(className,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
                            Text("${roster.size} دانش‌آموز · ${subjects.size} درس",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Groups,null,tint=MaterialTheme.colorScheme.primary)
                    }
                    subjects.forEach{lesson->Text("• $lesson",style=MaterialTheme.typography.bodyMedium)}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={onSelectClass(className);onNavigate(2)},modifier=Modifier.weight(1f)){Text("نمرات")}
                        OutlinedButton(onClick={onSelectClass(className);onNavigate(3)},modifier=Modifier.weight(1f)){Text("تحلیل")}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={onSelectClass(className);onNavigate(6)},modifier=Modifier.weight(1f)){Text("حضور")}
                        OutlinedButton(onClick={onSelectClass(className);onNavigate(7)},modifier=Modifier.weight(1f)){Text("برنامه")}
                    }
                    OutlinedButton(onClick={onSelectClass(className);onNavigate(1)},modifier=Modifier.fillMaxWidth()){Text("مدیریت دانش‌آموزان")}
                }
            }
        }
        Button(onClick={onNavigate(1)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PersonAdd,"");Text("افزودن کلاس / دانش‌آموز")}
        Text("دسترسی سریع",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            DashboardTile("طرح درس","طراحی آموزشی",Icons.Default.AutoAwesome,Modifier.weight(1f)){onNavigate(4)}
            DashboardTile("تنظیمات","مدیریت تنظیمات",Icons.Default.Settings,Modifier.weight(1f)){onNavigate(5)}
        }
    }
}
@Composable private fun DashboardStat(label:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier){
    Card(modifier=modifier,shape=MaterialTheme.shapes.large,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon,null,Modifier.padding(10.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun DashboardTile(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier,onClick:()->Unit){
    Card(modifier=modifier,shape=MaterialTheme.shapes.large,onClick=onClick,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon,null,Modifier.padding(12.dp).size(26.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { Icon(Icons.Default.ArrowForward,null,tint=MaterialTheme.colorScheme.primary) }
        }
    }
}
@Composable private fun YarMoallemTheme(content:@Composable ()->Unit){
    val colors=lightColorScheme(
        primary=androidx.compose.ui.graphics.Color(0xFF176B87),
        onPrimary=androidx.compose.ui.graphics.Color.White,
        primaryContainer=androidx.compose.ui.graphics.Color(0xFFD4EFF7),
        onPrimaryContainer=androidx.compose.ui.graphics.Color(0xFF073747),
        secondary=androidx.compose.ui.graphics.Color(0xFF3B7C70),
        secondaryContainer=androidx.compose.ui.graphics.Color(0xFFD8EEE8),
        background=androidx.compose.ui.graphics.Color(0xFFF4F8FA),
        surface=androidx.compose.ui.graphics.Color.White,
        onSurface=androidx.compose.ui.graphics.Color(0xFF172B35),
        onSurfaceVariant=androidx.compose.ui.graphics.Color(0xFF607580),
        outline=androidx.compose.ui.graphics.Color(0xFFD8E3E8)
    )
    MaterialTheme(colorScheme=colors,shapes=Shapes(
        extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        medium=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        large=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        extraLarge=androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    ),content=content)
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
fun jalaliToIso(yearText:String,monthText:String,dayText:String):String? {
    return try {
        val jy=yearText.toInt();val jm=monthText.toInt();val jd=dayText.toInt()
        if(jm !in 1..12 || jd !in 1..if(jm<=6)31 else 30) return null
        val jy2=jy-979;var jdn=365*jy2+(jy2/33)*8+(jy2%33+3)/4
        for(i in 1 until jm) jdn+=if(i<=6)31 else 30
        jdn+=jd-1
        var gdn=jdn+79;var gy=1600+400*(gdn/146097);gdn%=146097
        var leap=true
        if(gdn>=36525){gdn--;gy+=100*(gdn/36524);gdn%=36524;if(gdn>=365)gdn++ else leap=false}
        gy+=4*(gdn/1461);gdn%=1461
        if(gdn>=366){leap=false;gdn--;gy+=gdn/365;gdn%=365}
        val gd=gdn+1
        val months=intArrayOf(31,if(leap)29 else 28,31,30,31,30,31,31,30,31,30,31)
        var gm=1;var rem=gd
        while(gm<=12&&rem>months[gm-1]){rem-=months[gm-1];gm++}
        String.format(Locale.US,"%04d-%02d-%02d",gy,gm,rem)
    }catch(_:Exception){null}
}
fun jalaliDateLabel(iso:String):String {
    return try {
        val p=iso.split("-"); if(p.size!=3)return iso
        val gy=p[0].toInt(); val gm=p[1].toInt(); val gd=p[2].toInt()
        val gdm=intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var gy2=gy-1600; val gm2=gm-1; val gd2=gd-1
        var gdn=365*gy2+gy2/4-gy2/100+gy2/400+gdm[gm2]+gd2
        if(gm>2 && gy%4==0 && (gy%100!=0 || gy%400==0)) gdn++
        var jdn=gdn-79; val jnp=jdn/12053; jdn%=12053
        var jy=979+33*jnp+4*(jdn/1461); jdn%=1461
        if(jdn>=366){jy+=(jdn-1)/365;jdn=(jdn-1)%365}
        val jm=if(jdn<186)1+jdn/31 else 7+(jdn-186)/30
        val jd=if(jdn<186)1+jdn%31 else 1+(jdn-186)%30
        String.format(Locale("fa"),"%04d/%02d/%02d",jy,jm,jd)
    } catch(_:Exception) { iso }
}
fun normalizeDigits(s:String):String { val p="۰۱۲۳۴۵۶۷۸۹";val a="٠١٢٣٤٥٦٧٨٩";return s.map{ch->when{ch in p->(ch.code-p[0].code).toString();ch in a->(ch.code-a[0].code).toString();else->ch.toString()}}.joinToString("") }
fun makeBackup(students:List<Student>,columns:List<ScoreColumn>,attendance:JSONObject,timetable:String,apiKey:String):String = JSONObject().put("app","YarMoallem").put("version",3).put("apiKey",apiKey).put("createdAt",java.time.Instant.now().toString()).put("students",JSONArray(saveStudents(students))).put("columns",JSONArray(saveColumns(columns))).put("attendance",JSONObject(attendance.toString())).put("timetable",timetable).toString(2)
fun autoBackup(prefs:android.content.SharedPreferences,students:List<Student>,columns:List<ScoreColumn>,attendance:JSONObject,timetable:String){try{val json=makeBackup(students,columns,attendance,timetable,prefs.getString("api_key","") ?: "");val e=prefs.edit().putString("auto_backup_latest",json).putString("auto_backup_time",java.time.Instant.now().toString());for(i in 6 downTo 1){val old=prefs.getString("auto_backup_"+i,null);if(old!=null)e.putString("auto_backup_"+(i+1),old)};e.putString("auto_backup_1",json).apply()}catch(_:Exception){}}
fun average(scores:Map<String,Float>):Float?=scores.values.takeIf{it.isNotEmpty()}?.average()?.toFloat()
fun parseVoice(text:String):Pair<String,Float>?{
    val nums=Regex("""\d+(?:[٫.]\d+)?""").findAll(text).mapNotNull{it.value.replace('٫','.').toFloatOrNull()}.toList()
    if(nums.isEmpty())return null
    val score=nums.last()
    val name=text.replace(Regex("""\d+(?:[٫.]\d+)?"""),"").replace(Regex("""(نمره|امتحان|آزمون|درس|از|برای)"""),"").trim()
    return name.takeIf{it.isNotBlank()}?.let{it to score}
}
suspend fun generateLessonWithGroq(apiKey:String,request:String,mode:String="lesson"):String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val conn=(java.net.URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as java.net.HttpURLConnection)
        conn.requestMethod="POST";conn.connectTimeout=30000;conn.readTimeout=120000;conn.doOutput=true
        conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer ${apiKey.trim()}")
        val task=if(mode=="quiz")"""بر اساس تنظیمات آزمون که در درخواست آمده، آزمون بساز. دقیقاً نوع سؤال‌های انتخاب‌شده و تعداد تعیین‌شده برای هر نوع را رعایت کن؛ هیچ سؤال اضافی یا نوع انتخاب‌نشده تولید نکن. درجه دشواری انتخاب‌شده را در همه سؤال‌ها رعایت کن. اگر پاسخنامه درخواست شده، آن را در بخش جداگانه با پاسخ صحیح، پاسخ تشریحی کوتاه و بارم هر سؤال ارائه کن؛ در غیر این صورت پاسخ‌ها را نمایش نده. خروجی مختصر، مرتب و بدون مقدمه طولانی باشد. مطالب تأییدنشده را به کتاب نسبت نده.""" else """طرح درس حرفه‌ای تولید کن: اهداف رفتاری، پیش‌نیاز، وسایل، ایجاد انگیزه، مراحل تدریس با زمان، فعالیت معلم و دانش‌آموز، مثال‌های کتاب، ارزشیابی آغازین/تکوینی/پایانی، تکلیف و تمایز آموزشی برای دانش‌آموزان ضعیف و قوی. برای ریاضی حل مثال‌ها مرحله‌به‌مرحله و مناسب پایه باشد."""
        val prompt="""درخواست: $request
ابتدا با browser_search کتاب رسمی آموزش‌وپرورش ایران، PDF کتاب و منابع معتبر مانند chap.sch.ir و رشد را جستجو کن. پایه، عنوان فصل و درس و محتوای دقیق را تطبیق بده. فقط محتوای بررسی‌شده را به کتاب نسبت بده. اگر متن یا منبع دقیق پیدا نشد، صریح بنویس «متن دقیق کتاب تأیید نشد» و محتوای عمومی را از مطالب کتاب جدا کن؛ هیچ صفحه، تمرین یا مثال ساختگی نکن. اگر مشخصات مبهم است، ابهام را اعلام کن.
$task
خروجی فارسی، منظم و کاربردی باشد؛ منابع و لینک‌های بررسی‌شده و میزان اطمینان را ذکر کن."""
        val body=JSONObject().put("model","openai/gpt-oss-120b")
            .put("messages",JSONArray().put(JSONObject().put("role","system").put("content","دستیار پژوهش آموزشی و طراحی درس؛ جستجوی وب را انجام بده و از جعل منابع پرهیز کن.")).put(JSONObject().put("role","user").put("content",prompt)))
            .put("temperature",0.3).put("max_completion_tokens",6000)
        conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
        val code=conn.responseCode;val raw=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()} ?: ""
        if(code !in 200..299){val err=try{JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()}catch(_:Exception){""};when(code){401->"کلید Groq معتبر نیست.";403->"دسترسی به جستجوی وب یا مدل مجاز نیست.";429->"محدودیت درخواست Groq؛ کمی بعد تلاش کنید.";else->"خطای Groq ($code): ${err.ifBlank{"درخواست ناموفق"}}"}}
        else JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty().ifBlank{"پاسخ جستجو خالی بود؛ درخواست را دقیق‌تر کنید."}
    }catch(e:Exception){"جستجو ناموفق بود: ${e.message ?: "خطای نامشخص"}"}
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

fun classAverage(students:List<Student>):Float?{val v=students.mapNotNull{average(it.scores)};return if(v.isEmpty())null else v.average().toFloat()}
fun scoreStatus(a:Float?):String=when{a==null->"بدون نمره";a>=17->"بسیار خوب";a>=14->"خوب";a>=12->"قابل قبول";else->"نیازمند پیگیری"}
fun htmlEsc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
fun shareGradebook(ctx:android.content.Context,clazz:String,subject:String,students:List<Student>,columns:List<ScoreColumn>){
 val rows=students.sortedBy{it.name}.joinToString(""){st->"<tr><td>"+htmlEsc(st.name)+"</td>"+columns.joinToString(""){c->"<td>"+(st.scores[c.name]?.toString()?: "—")+"</td>"}+"<td>"+(average(st.scores)?.let{"%.2f".format(it)}?: "—")+"</td></tr>"}
 val html="<html><head><meta charset='utf-8'><style>body{font-family:Arial;direction:rtl}table{border-collapse:collapse;width:100%}th,td{border:1px solid #555;padding:8px;text-align:center}th{background:#d4eff7}</style></head><body dir='rtl'><h2>دفتر نمرات</h2><p>کلاس: "+htmlEsc(clazz)+" | درس: "+htmlEsc(subject)+"</p><table><tr><th>نام دانش‌آموز</th>"+columns.joinToString(""){"<th>"+htmlEsc(it.name)+"</th>"}+"<th>میانگین</th></tr>"+rows+"</table></body></html>"
 val i=Intent(Intent.ACTION_SEND).apply{type="text/html";putExtra(Intent.EXTRA_SUBJECT,"دفتر نمرات "+clazz+" - "+subject);putExtra(Intent.EXTRA_TEXT,html)};ctx.startActivity(Intent.createChooser(i,"ارسال جدول؛ قابل باز کردن در Word"))
}
fun shareAnalysis(ctx:android.content.Context,clazz:String,subject:String,students:List<Student>,columns:List<ScoreColumn>){
 val rows=students.sortedBy{it.name}.joinToString(""){st->"<tr><td>"+htmlEsc(st.name)+"</td><td>"+(average(st.scores)?.let{"%.2f".format(it)}?: "—")+"</td><td>"+htmlEsc(scoreStatus(average(st.scores)))+"</td><td>"+st.scores.size+"</td><td>"+columns.filter{it.name !in st.scores}.joinToString("،"){htmlEsc(it.name)}+"</td></tr>"}
 val html="<html><head><meta charset='utf-8'><style>body{font-family:Arial;direction:rtl}table{border-collapse:collapse;width:100%}th,td{border:1px solid #555;padding:8px;text-align:center}th{background:#d4eff7}</style></head><body dir='rtl'><h2>تحلیل عملکرد</h2><p>کلاس: "+htmlEsc(clazz)+" | درس: "+htmlEsc(subject)+"</p><table><tr><th>نام</th><th>میانگین</th><th>وضعیت</th><th>نمرات ثبت‌شده</th><th>نمرات ناقص</th></tr>"+rows+"</table></body></html>"
 val i=Intent(Intent.ACTION_SEND).apply{type="text/html";putExtra(Intent.EXTRA_SUBJECT,"تحلیل "+clazz+" - "+subject);putExtra(Intent.EXTRA_TEXT,html)};ctx.startActivity(Intent.createChooser(i,"ارسال گزارش؛ قابل باز کردن در Word"))
}