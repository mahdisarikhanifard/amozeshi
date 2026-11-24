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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

data class Student(val name:String, val clazz:String, val subject:String, val scores:Map<String,Float>, val studentNumber:String = "")
data class ScoreColumn(val name:String, val max:Float=20f, val weight:Float=1f)

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { YarMoallemTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App() } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App() {
    val ctx= LocalContext.current
    val prefs=ctx.getSharedPreferences("yar_moallem",0)
    val securePrefs=remember { createSecurePreferences(ctx) }
    var students by remember { mutableStateOf(loadStudents(prefs.getString("students","[]") ?: "[]")) }
    var columns by remember { mutableStateOf(loadColumns(prefs.getString("columns","[\"مستمر\",\"شفاهی\",\"آزمون\"]") ?: "[]")) }
    var selectedTab by remember { mutableStateOf(0) }
    var settingsPage by remember { mutableStateOf("") }
    var attendanceDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var attendance by remember { mutableStateOf(JSONObject(prefs.getString("attendance","{}") ?: "{}")) }
    var timetable by remember { mutableStateOf(prefs.getString("timetable","") ?: "") }
    var timetableEdit by remember { mutableStateOf(timetable) }
    var selectedClass by remember { mutableStateOf(students.firstOrNull()?.clazz ?: "") }
    var selectedSubject by remember { mutableStateOf(students.firstOrNull { it.clazz == selectedClass }?.subject ?: "") }
    var nameInput by remember { mutableStateOf("") }
    var studentNumberInput by remember { mutableStateOf("") }
    var classInput by remember { mutableStateOf(students.firstOrNull()?.clazz ?: "") }
    var subjectInput by remember { mutableStateOf(students.firstOrNull()?.subject ?: "") }
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
    var restoreMessage by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(securePrefs.getString("api_key",null) ?: prefs.getString("api_key","") ?: "") }
    var showApiKey by remember { mutableStateOf(false) }
    var apiTestLoading by remember { mutableStateOf(false) }
    var apiTestResult by remember { mutableStateOf("") }
    var editStudent by remember { mutableStateOf<Student?>(null) }
    var editName by remember { mutableStateOf("") }
    var editStudentNumber by remember { mutableStateOf("") }
    var editClazz by remember { mutableStateOf("") }
    var editSubject by remember { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (securePrefs.getString("api_key",null)==null) {
            val legacy=prefs.getString("api_key",null)
            if (!legacy.isNullOrBlank()) securePrefs.edit().putString("api_key",legacy).apply()
            prefs.edit().remove("api_key").apply()
        }
    }
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
            selectedClass=newStudents.firstOrNull()?.clazz ?: "";selectedSubject=newStudents.firstOrNull { it.clazz==selectedClass }?.subject ?: ""
            restoreMessage="بازیابی کامل شد؛ ${newStudents.size} دانش‌آموز بازیابی شد."
            securePrefs.edit().putString("api_key",apiKey).apply()
            prefs.edit().putString("students",saveStudents(newStudents)).putString("columns",saveColumns(newColumns)).putString("attendance",newAttendance.toString()).putString("timetable",newTimetable).remove("api_key").apply()
            autoBackup(prefs,newStudents,newColumns,newAttendance,newTimetable,apiKey)

        } catch(e:Exception){restoreMessage="بازیابی انجام نشد؛ اطلاعات قبلی حفظ شد. ${e.message}"}
    }
    var lessonLoading by remember { mutableStateOf(false) }
    fun persist(studentData:List<Student> = students, columnData:List<ScoreColumn> = columns) { prefs.edit().putString("students",saveStudents(studentData)).putString("columns",saveColumns(columnData)).apply(); autoBackup(prefs,studentData,columnData,attendance,timetable,apiKey) }
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
        bottomBar={ NavigationBar {
            val tabs=listOf(0 to ("خانه" to Icons.Default.Home),1 to ("کلاس" to Icons.Default.Groups),2 to ("نمرات" to Icons.Default.Assignment),6 to ("حضور" to Icons.Default.CheckCircle),8 to ("بیشتر" to Icons.Default.Menu))
            tabs.forEach { (index,item) -> NavigationBarItem(selected=selectedTab==index || (index==8 && selectedTab in listOf(3,4,5,7)),onClick={selectedTab=index},icon={Icon(item.second,item.first)},label={Text(item.first)}) }
        } },
        snackbarHost={SnackbarHost(remember { SnackbarHostState() }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(selectedTab) {
                0 -> HomeScreen(students,columns,onNavigate={selectedTab=it},selectedClass,selectedSubject,onSelectClass={selectedClass=it;selectedSubject=students.filter{st->st.clazz==it}.map{st->st.subject}.distinct().firstOrNull() ?: selectedSubject})
                1 -> {
                    Text("مدیریت کلاس‌ها", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
                    OutlinedTextField(nameInput, { nameInput=it }, label={Text("نام دانش‌آموز")}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(studentNumberInput, { studentNumberInput=it }, label={Text("شماره دانش‌آموزی")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                    OutlinedTextField(classInput, { classInput=it }, label={Text("کلاس / پایه")}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(subjectInput, { subjectInput=it }, label={Text("درس")}, modifier=Modifier.fillMaxWidth())
                    Button(onClick={
                        if(nameInput.isNotBlank() && studentNumberInput.isNotBlank() && classInput.isNotBlank() && subjectInput.isNotBlank()) {
                            val number=studentNumberInput.trim()
                            val conflicting=students.any{it.studentNumber==number && (!it.name.equals(nameInput.trim(),true) || it.clazz!=classInput.trim())}
                            val exists=students.any{it.studentNumber==number && it.clazz==classInput.trim() && it.subject==subjectInput.trim()}
                            if(conflicting) snackbar="این شماره دانش‌آموزی قبلاً برای فرد دیگری ثبت شده است."
                            else if(!exists) {
                                students=students+Student(nameInput.trim(),classInput.trim(),subjectInput.trim(),emptyMap(),number)
                                persist()
                                snackbar="دانش‌آموز ثبت شد."
                                nameInput="";studentNumberInput=""
                            } else snackbar="این دانش‌آموز قبلاً در این درس ثبت شده است."
                        } else snackbar="نام، شماره دانش‌آموزی، کلاس و درس را کامل کنید."
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
                                        Text("شماره: ${st.studentNumber}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        IconButton(onClick={editStudent=st;editName=st.name;editStudentNumber=st.studentNumber;editClazz=st.clazz;editSubject=st.subject}){Icon(Icons.Default.Edit,"ویرایش")}
                                        IconButton(onClick={students=students.filterNot{it==st};persist()}){Icon(Icons.Default.Delete,"حذف")}
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Text("دفتر نمرات",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                        val classOptions=students.map{it.clazz}.distinct().sorted()
                    var classMenu by remember{mutableStateOf(false)}
                    ExposedDropdownMenuBox(expanded=classMenu,onExpandedChange={classMenu=it}){OutlinedTextField(selectedClass,{selectedClass=it},readOnly=true,label={Text("کلاس")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(classMenu)},modifier=Modifier.fillMaxWidth().menuAnchor());ExposedDropdownMenu(expanded=classMenu,onDismissRequest={classMenu=false}){classOptions.forEach{DropdownMenuItem(text={Text(it)},onClick={selectedClass=it;selectedSubject=students.firstOrNull{st->st.clazz==it}?.subject ?: "";classMenu=false})}}}
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
                    columns.forEach{c->val currentScore=st.scores[c.name];var v by remember(st.studentNumber,st.name,st.clazz,st.subject,c.name,currentScore){mutableStateOf(currentScore?.toString().orEmpty())};OutlinedTextField(value=v,onValueChange={input->v=input;val normalized=normalizeDigits(input).replace("٫",".").replace(",",".");val n=normalized.toFloatOrNull();if(n!=null&&n in 0f..c.max){val updated=students.map{row->if(row.studentNumber==st.studentNumber&&row.clazz==st.clazz&&row.subject==st.subject)row.copy(scores=row.scores+(c.name to n))else row};students=updated;persist(updated)}else if(input.isBlank()){val updated=students.map{row->if(row.studentNumber==st.studentNumber&&row.clazz==st.clazz&&row.subject==st.subject)row.copy(scores=row.scores-c.name)else row};students=updated;persist(updated)}},label={Text(c.name+" (از "+c.max+")")},singleLine=true,modifier=Modifier.fillMaxWidth())}
                    Text(scoreStatus(average(st.scores)),color=MaterialTheme.colorScheme.secondary)
                    }}}
                    Button(onClick={shareGradebook(ctx,selectedClass,selectedSubject,roster,columns)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileDownload,"");Text("خروجی جدول در Word")}
                }
                3 -> {
                    Text("تحلیل عملکرد کلاس",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
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
                    val orderedAverages=report.mapNotNull{average(it.scores)}.sorted()
                    val median=if(orderedAverages.isEmpty())null else if(orderedAverages.size%2==1)orderedAverages[orderedAverages.size/2] else (orderedAverages[orderedAverages.size/2-1]+orderedAverages[orderedAverages.size/2])/2f
                    val totalPossible=report.size*columns.size
                    val totalEntered=report.sumOf{st->columns.count{it.name in st.scores}}
                    val completion=if(totalPossible==0)0 else totalEntered*100/totalPossible
                    val strongestColumn=columnAnalysis.filter{it.second!=null}.maxByOrNull{it.second!!}
                    val weakestColumn=columnAnalysis.filter{it.second!=null}.minByOrNull{it.second!!}
                    val high=report.count{(average(it.scores)?:-1f)>=17};val low=report.count{val a=average(it.scores);a!=null&&a<12};val missing=report.count{it.scores.isEmpty()}
                    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(selectedClass+" · "+selectedSubject,fontWeight=FontWeight.Bold);Text("میانگین کلاس: "+(classAvg?.let{"%.2f از ۲۰".format(it)}?: "—"),style=MaterialTheme.typography.titleLarge);Text("میانه: "+(median?.let{"%.2f".format(it)}?: "—")+" | پوشش ثبت نمرات: "+completion+"٪");LinearProgressIndicator(progress={completion/100f},modifier=Modifier.fillMaxWidth());Text("دانش‌آموزان: "+report.size+" | دارای نمره: "+scoredCount+" | عملکرد قوی (۱۷+): "+high+" | نیازمند توجه (<۱۲): "+low+" | بدون نمره: "+missing);Text("میانگین فقط از نمرات موجود محاسبه می‌شود؛ تکمیل ارزیابی برای نتیجه دقیق‌تر ضروری است.",style=MaterialTheme.typography.bodySmall)}}
                    Text("تحلیل نقاط قوت و نیازمند تقویت",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);if(strongestColumn!=null)Text("قوی‌ترین حوزه: "+strongestColumn.first.name+" ("+"%.2f".format(strongestColumn.second)+")");if(weakestColumn!=null)Text("نیازمند تقویت: "+weakestColumn.first.name+" ("+"%.2f".format(weakestColumn.second)+")");Text("میانگین هر ستون",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);columnAnalysis.forEach{(col,avg)->Card{Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(col.name);Text(avg?.let{"%.2f از ۲۰".format(it)}?: "بدون نمره",fontWeight=FontWeight.Bold)};LinearProgressIndicator(progress={((avg?:0f)/20f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())}}};if(bestStudents.isNotEmpty()){Text("۳ دانش‌آموز برتر",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);bestStudents.forEach{(st,avg)->Text("${st.name} — %.2f".format(avg))}};Text("توزیع میانگین",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    listOf("۱۸ تا ۲۰" to report.count{(average(it.scores)?:-1f)>=18},"۱۶ تا ۱۸" to report.count{val a=average(it.scores);a!=null&&a>=16&&a<18},"۱۴ تا ۱۶" to report.count{val a=average(it.scores);a!=null&&a>=14&&a<16},"۱۲ تا ۱۴" to report.count{val a=average(it.scores);a!=null&&a>=12&&a<14},"کمتر از ۱۲" to low,"بدون نمره" to missing).forEach{(label,count)->Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));LinearProgressIndicator(progress={if(report.isEmpty())0f else count.toFloat()/report.size},Modifier.weight(1f));Text(count.toString(),Modifier.width(32.dp))}}
                    Button(onClick={shareAnalysis(ctx,selectedClass,selectedSubject,report,columns)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileDownload,"");Text("خروجی تحلیل در Word")}
                    report.forEach{StudentAnalysisCard(it,columns,report)}
                }
                4 -> {
                    Text("دستیار آموزشی",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    
                    OutlinedTextField(lessonRequest,{lessonRequest=it},label={Text("پایه، درس و عنوان دقیق مبحث")},placeholder={Text("مثلاً پایه هفتم، علوم، فصل ۳، اتم‌ها")},modifier=Modifier.fillMaxWidth(),minLines=2)
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
                }
                5 -> {
                    Text("تنظیمات",style=MaterialTheme.typography.headlineSmall)
                    if(settingsPage.isBlank()) {
                        listOf("دفتر نمرات","هوش مصنوعی","پشتیبان‌گیری","درباره برنامه").forEach { page ->
                            Card(onClick={settingsPage=page},modifier=Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text(page,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Icon(Icons.Default.ChevronLeft,null)} }
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
                            "هوش مصنوعی" -> {
                                OutlinedTextField(value=apiKey,onValueChange={apiKey=it;securePrefs.edit().putString("api_key",it.trim()).apply();apiTestResult=""},label={Text("کلید API Groq")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=if(showApiKey)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={showApiKey=!showApiKey}){Icon(if(showApiKey)Icons.Default.VisibilityOff else Icons.Default.Visibility,null)}})
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                                    Button(onClick={securePrefs.edit().putString("api_key",apiKey.trim()).apply();autoBackup(prefs,students,columns,attendance,timetable,apiKey);snackbar="کلید ذخیره شد."},modifier=Modifier.weight(1f)){Icon(Icons.Default.Save,null);Text("ذخیره کلید")}
                                    OutlinedButton(onClick={
                                        if(apiKey.isBlank()){apiTestResult="کلید را وارد کنید."}else{apiTestLoading=true;apiTestResult="";scope.launch{apiTestResult=testGroqConnection(apiKey);apiTestLoading=false}}
                                    },modifier=Modifier.weight(1f),enabled=!apiTestLoading){Text(if(apiTestLoading)"در حال بررسی…" else "آزمایش اتصال")}
                                }
                                if(apiTestResult.isNotBlank()) Text(apiTestResult,color=if(apiTestResult.contains("موفق"))MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            "پشتیبان‌گیری" -> {
                                Button(onClick={exportLauncher.launch("YarMoallem_Backup_${java.time.LocalDate.now()}.json")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Backup,"");Text("ذخیره فایل پشتیبان")}
                                Button(onClick={importLauncher.launch(arrayOf("application/json","text/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Restore,"");Text("بازیابی از فایل پشتیبان")}
                            
                                if(restoreMessage.isNotBlank()) Text(restoreMessage,color=if(restoreMessage.startsWith("بازیابی کامل"))MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            "درباره برنامه" -> {
                                Card(modifier=Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                        Text("یار معلم",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                                        Text("این برنامه توسط مهدی ساریخانی فرد طراحی شده است.")
                                        Text("هدف این برنامه، زمینه‌سازی برای آموزش بهتر، هدفمندتر و اثربخش‌تر است؛ ابزاری برای کمک به معلم تا زمان و اطلاعات آموزشی را بهتر مدیریت کند و فرصت بیشتری برای توجه به یادگیری دانش‌آموزان داشته باشد.")
                                        Text("این برنامه با فراهم کردن امکاناتی مانند مدیریت کلاس و دانش‌آموزان، ثبت نمرات، تحلیل عملکرد، حضور و غیاب، برنامه هفتگی و دستیار هوشمند آموزشی، تلاش می‌کند فرایند برنامه‌ریزی، پیگیری و ارزیابی آموزش را ساده‌تر کند.")
                                        Text("فناوری و هوش مصنوعی در این برنامه نقش ابزار کمکی دارند و جایگزین دانش، تجربه و تصمیم حرفه‌ای معلم نیستند. هدف نهایی، پشتیبانی از معلم و فراهم کردن زمینه‌ای بهتر برای رشد و یادگیری هر دانش‌آموز.")
                                    }
                                }
                            }
                        }
                    }
                }
            8 -> {
                Text("بخش‌های برنامه",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                listOf(3 to ("تحلیل عملکرد" to Icons.Default.Insights),4 to ("طرح درس و آزمون‌ساز" to Icons.Default.AutoAwesome),7 to ("برنامه هفتگی" to Icons.Default.CalendarMonth),5 to ("تنظیمات" to Icons.Default.Settings)).forEach{(tab,item)->
                    Card(onClick={selectedTab=tab;settingsPage=""},modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(item.second,null,tint=MaterialTheme.colorScheme.primary);Text(item.first,Modifier.weight(1f).padding(horizontal=12.dp),fontWeight=FontWeight.SemiBold);Icon(Icons.Default.ChevronLeft,null)}}
                }
            }
            6 -> {
                Text("حضور و غیاب",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                val attendanceClasses=students.map{it.clazz}.distinct().sorted()
                var attendanceClassMenu by remember{mutableStateOf(false)}
                ExposedDropdownMenuBox(expanded=attendanceClassMenu,onExpandedChange={attendanceClassMenu=it}) {
                    OutlinedTextField(selectedClass,{selectedClass=it},readOnly=true,label={Text("انتخاب کلاس")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(attendanceClassMenu)},modifier=Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded=attendanceClassMenu,onDismissRequest={attendanceClassMenu=false}) { attendanceClasses.forEach{cl->DropdownMenuItem(text={Text(cl)},onClick={selectedClass=cl;attendanceClassMenu=false;selectedSubject=students.filter{it.clazz==cl}.map{it.subject}.distinct().firstOrNull() ?: ""})} }
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
                val roster=students.filter{it.clazz==selectedClass&&it.subject==selectedSubject}.distinctBy{it.studentNumber}.sortedBy{it.name}
                val lessonKey=selectedClass+"::"+selectedSubject
                val dateRecord=attendance.optJSONObject(attendanceDate)?:JSONObject()
                val day=dateRecord.optJSONObject(lessonKey)?:JSONObject()
                Text("کلاس: $selectedClass · درس: $selectedSubject",fontWeight=FontWeight.SemiBold)
                Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
                    Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){
                        Text("حاضر: "+roster.count{day.optString(it.studentNumber)=="present"})
                        Text("غایب: "+roster.count{day.optString(it.studentNumber)=="absent"})
                        Text("ثبت‌نشده: "+roster.count{day.optString(it.studentNumber).isBlank()})
                    }
                }
                Text("ثبت وضعیت روزانه",fontWeight=FontWeight.Bold)
                roster.forEach{st->
                    val status=day.optString(st.studentNumber)
                    Card { Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text(st.name,fontWeight=FontWeight.SemiBold);Text("شماره: ${st.studentNumber}",style=MaterialTheme.typography.bodySmall)}
                        FilterChip(selected=status=="present",onClick={
                            val next=JSONObject(attendance.toString());val date=JSONObject((next.optJSONObject(attendanceDate)?:JSONObject()).toString());val lesson=JSONObject((date.optJSONObject(lessonKey)?:JSONObject()).toString())
                            lesson.put(st.studentNumber,"present");date.put(lessonKey,lesson);next.put(attendanceDate,date);attendance=next
                            prefs.edit().putString("attendance",next.toString()).apply();autoBackup(prefs,students,columns,next,timetable,apiKey)
                        },label={Text("حاضر")})
                        Spacer(Modifier.width(5.dp))
                        FilterChip(selected=status=="absent",onClick={
                            val next=JSONObject(attendance.toString());val date=JSONObject((next.optJSONObject(attendanceDate)?:JSONObject()).toString());val lesson=JSONObject((date.optJSONObject(lessonKey)?:JSONObject()).toString())
                            lesson.put(st.studentNumber,"absent");date.put(lessonKey,lesson);next.put(attendanceDate,date);attendance=next
                            prefs.edit().putString("attendance",next.toString()).apply();autoBackup(prefs,students,columns,next,timetable,apiKey)
                        },label={Text("غایب")})
                    }}
                }
                Text("گزارش کلی این درس",fontWeight=FontWeight.Bold)
                val lessonDates=attendance.keys().asSequence().filter{key->(attendance.optJSONObject(key)?.optJSONObject(lessonKey))!=null}.sorted().toList()
                val totalPresent=lessonDates.sumOf{key->val rec=attendance.optJSONObject(key)?.optJSONObject(lessonKey)?:JSONObject();roster.count{rec.optString(it.studentNumber)=="present"}}
                val totalAbsent=lessonDates.sumOf{key->val rec=attendance.optJSONObject(key)?.optJSONObject(lessonKey)?:JSONObject();roster.count{rec.optString(it.studentNumber)=="absent"}}
                Card { Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text("روزهای دارای ثبت: ${lessonDates.size}")
                    Text("مجموع حضورها: $totalPresent · مجموع غیبت‌ها: $totalAbsent")
                    Text("درصد حضور ثبت‌شده: "+if(totalPresent+totalAbsent==0)"—" else "${(totalPresent*100/(totalPresent+totalAbsent))}%")
                }}
                Text("گزارش هر دانش‌آموز",fontWeight=FontWeight.Bold)
                roster.forEach{st->
                    val present=lessonDates.sumOf{key->if(attendance.optJSONObject(key)?.optJSONObject(lessonKey)?.optString(st.studentNumber)=="present") 1L else 0L}
                    val absent=lessonDates.sumOf{key->if(attendance.optJSONObject(key)?.optJSONObject(lessonKey)?.optString(st.studentNumber)=="absent") 1L else 0L}
                    Card { Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Text(st.name+" · "+st.studentNumber,fontWeight=FontWeight.SemiBold)
                        Text("حضور: $present · غیبت: $absent · ثبت‌نشده: ${(lessonDates.size-present-absent).coerceAtLeast(0)}")
                    }}
                }
                Text("سوابق ماه انتخاب‌شده",fontWeight=FontWeight.Bold)
                val monthRecords=lessonDates.filter{it.startsWith(attendanceDate.take(7))}
                if(monthRecords.isEmpty()) Text("در این ماه برای این درس سابقه‌ای ثبت نشده است.") else monthRecords.forEach{dateKey->
                    val rec=attendance.optJSONObject(dateKey)?.optJSONObject(lessonKey)?:JSONObject()
                    Card { Column(Modifier.padding(10.dp)){
                        Text(jalaliDateLabel(dateKey),fontWeight=FontWeight.Bold)
                        Text("حاضر: ${roster.count{rec.optString(it.studentNumber)=="present"}} · غایب: ${roster.count{rec.optString(it.studentNumber)=="absent"}}")
                    }}
                }
                Button(onClick={
                    val report=buildString{
                        append("گزارش حضور و غیاب\nکلاس: $selectedClass\nدرس: $selectedSubject\nتاریخ: ${jalaliDateLabel(attendanceDate)}\n\n")
                        roster.forEach{st->append("${st.name} (${st.studentNumber}): ");append(when(day.optString(st.studentNumber)){"present"->"حاضر";"absent"->"غایب";else->"ثبت نشده"});append("\n")}
                    }
                    val sendIntent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"حضور و غیاب $selectedClass - $selectedSubject");putExtra(Intent.EXTRA_TEXT,report)}
                    ctx.startActivity(Intent.createChooser(sendIntent,"اشتراک گزارش حضور"))
                },modifier=Modifier.fillMaxWidth()){Text("خروجی گزارش روزانه")}
            }
            7 -> {
                Text("برنامه هفتگی",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                OutlinedTextField(timetableEdit,{timetableEdit=it},modifier=Modifier.fillMaxWidth(),minLines=6,label={Text("شنبه | ۸ تا ۹ | نهم ۱ | مطالعات")})
                val timetableRows=timetableEdit.lines().mapNotNull{line->val p=line.split("|").map{it.trim()};if(p.size>=4&&p[0].isNotBlank())p else null}
                if(timetableRows.isNotEmpty()) {
                    Text("پیش‌نمایش جدول",fontWeight=FontWeight.Bold)
                    timetableRows.forEach { row -> Card { Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){row.take(4).forEach{Text(it,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)}} } }
                }
                Button(onClick={timetable=timetableEdit;prefs.edit().putString("timetable",timetable).apply();autoBackup(prefs,students,columns,attendance,timetable,apiKey);snackbar="برنامه هفتگی ذخیره شد."},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Save,"");Text("ذخیره برنامه")}
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
            if(editStudent!=null) AlertDialog(
                onDismissRequest={editStudent=null},
                title={Text("ویرایش دانش‌آموز")},
                text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(editName,{editName=it},label={Text("نام دانش‌آموز")},singleLine=true)
                    OutlinedTextField(editStudentNumber,{editStudentNumber=it},label={Text("شماره دانش‌آموزی")},singleLine=true)
                    OutlinedTextField(editClazz,{editClazz=it},label={Text("کلاس / پایه")},singleLine=true)
                    OutlinedTextField(editSubject,{editSubject=it},label={Text("درس")},singleLine=true)
                }},
                confirmButton={TextButton(onClick={
                    val old=editStudent
                    if(old!=null&&editName.isNotBlank()&&editStudentNumber.isNotBlank()&&editClazz.isNotBlank()&&editSubject.isNotBlank()){
                        val number=editStudentNumber.trim()
                        val conflict=students.any{it!=old && it.studentNumber==number && (!it.name.equals(editName.trim(),true) || it.clazz!=editClazz.trim())}
                        if(conflict) snackbar="این شماره دانش‌آموزی برای فرد دیگری ثبت شده است."
                        else { students=students.map{if(it==old)old.copy(name=editName.trim(),studentNumber=number,clazz=editClazz.trim(),subject=editSubject.trim())else it};persist() }
                    }
                    editStudent=null
                }){Text("ذخیره")}},
                dismissButton={TextButton(onClick={editStudent=null}){Text("انصراف")}}
            )

        }
    }
}

@Composable fun HomeScreen(students:List<Student>,columns:List<ScoreColumn>,onNavigate:(Int)->Unit,clazz:String,subject:String,onSelectClass:(String)->Unit){
    val classes=students.groupBy{it.clazz}.toSortedMap()
    val scored=students.count{it.scores.isNotEmpty()}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("یار معلم",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer)
                Button(onClick={onNavigate(1)},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.PersonAdd,""); Text("افزودن دانش‌آموز یا کلاس") }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            DashboardStat("کلاس‌ها",classes.size.toString(),Icons.Default.Groups,Modifier.weight(1f))
            DashboardStat("دانش‌آموزان",students.size.toString(),Icons.Default.People,Modifier.weight(1f))
            DashboardStat("دارای نمره",scored.toString(),Icons.Default.AssignmentTurnedIn,Modifier.weight(1f))
        }
        Text("دسترسی سریع",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            DashboardTile("حضور و غیاب","ثبت و پیگیری حضور",Icons.Default.CheckCircle,Modifier.weight(1f)){onNavigate(6)}
            DashboardTile("دفتر نمرات","ثبت و مشاهده نمرات",Icons.Default.Assignment,Modifier.weight(1f)){onNavigate(2)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            DashboardTile("طرح درس","ساخت طرح درس کوتاه",Icons.Default.AutoAwesome,Modifier.weight(1f)){onNavigate(4)}
            DashboardTile("برنامه هفتگی","مشاهده برنامه کلاس",Icons.Default.CalendarMonth,Modifier.weight(1f)){onNavigate(7)}
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()) {
            Text("کلاس‌های من",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("${classes.size} کلاس",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(classes.isEmpty()) {
            Card { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("هنوز کلاسی ثبت نشده است.")
                
                Button(onClick={onNavigate(1)}){Text("ایجاد اولین کلاس")}
            } }
        } else classes.forEach{(className,roster)->
            val subjects=roster.map{it.subject}.distinct()
            Card(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Groups,null,Modifier.padding(10.dp),tint=MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                            Text(className,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                            Text("${roster.size} دانش‌آموز · ${subjects.size} درس",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${roster.count{it.scores.isNotEmpty()}} نمره‌دار",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    }
                    Text(subjects.joinToString("  •  "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={onSelectClass(className);onNavigate(2)},modifier=Modifier.weight(1f)){Text("نمرات")}
                        OutlinedButton(onClick={onSelectClass(className);onNavigate(3)},modifier=Modifier.weight(1f)){Text("تحلیل")}
                        OutlinedButton(onClick={onSelectClass(className);onNavigate(6)},modifier=Modifier.weight(1f)){Text("حضور")}
                    }
                }
            }
        }
        OutlinedButton(onClick={onNavigate(5)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Settings,"");Text("تنظیمات برنامه")}
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
@Composable fun StudentAnalysisCard(s:Student,columns:List<ScoreColumn>,classmates:List<Student> = emptyList()){
    val avg=average(s.scores)
    val peerAverages=classmates.filter{it!=s}.mapNotNull{average(it.scores)}
    val peerAvg=if(peerAverages.isEmpty())null else peerAverages.average().toFloat()
    val completed=columns.count{it.name in s.scores}
    val missing=columns.filter{it.name !in s.scores}.map{it.name}
    val ranked=s.scores.entries.sortedBy{it.value}
    val weakest=ranked.firstOrNull()
    val strongest=ranked.lastOrNull()
    val lowCount=s.scores.count{it.value<12f}
    val delta=if(avg!=null&&peerAvg!=null)avg-peerAvg else null
    val completion=if(columns.isEmpty())100 else completed*100/columns.size
    val recommendation=when {
        avg==null -> "هنوز نمره‌ای ثبت نشده؛ ابتدا ارزیابی‌های این دانش‌آموز را تکمیل کنید."
        completion<50 -> "اطلاعات ارزیابی ناقص است؛ پیش از قضاوت، ستون‌های باقی‌مانده را تکمیل کنید."
        avg<12f -> "نیازمند حمایت آموزشی؛ مبحث‌های ضعیف را به بخش‌های کوچک تقسیم و با تمرین مرحله‌ای و بازخورد پیگیری کنید."
        lowCount>0 -> "در ستون‌های ضعیف‌تر تمرین هدفمند ارائه کنید و در ارزیابی بعدی میزان پیشرفت را بسنجید."
        avg>=17f -> "عملکرد قوی؛ از فعالیت‌های تعمیقی و مسئله‌های چالشی برای توسعه یادگیری استفاده کنید."
        else -> "یادگیری در سطح قابل قبول است؛ مرور منظم و تمرین متنوع به تثبیت آموخته‌ها کمک می‌کند."
    }
    Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(s.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
        Text("${s.clazz} · ${s.subject}",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("میانگین نمرات ثبت‌شده: ${avg?.let{"%.2f از ۲۰".format(it)} ?: "بدون نمره"}",fontWeight=FontWeight.SemiBold)
        Text("تکمیل ارزیابی: $completed از ${columns.size} ستون ($completion٪)")
        if(delta!=null) Text(if(delta>=0) "بالاتر از میانگین هم‌کلاسی‌ها به میزان %.2f".format(delta) else "پایین‌تر از میانگین هم‌کلاسی‌ها به میزان %.2f".format(-delta))
        if(strongest!=null) Text("نقطه قوت: ${strongest.key} (${"%.2f".format(strongest.value)})")
        if(weakest!=null&&weakest.key!=strongest?.key) Text("نیازمند تقویت: ${weakest.key} (${"%.2f".format(weakest.value)})")
        if(missing.isNotEmpty()) Text("ارزیابی‌های ثبت‌نشده: ${missing.joinToString("، ")}")
        Text("پیشنهاد آموزشی: $recommendation",color=MaterialTheme.colorScheme.primary)
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
fun autoBackup(prefs:android.content.SharedPreferences,students:List<Student>,columns:List<ScoreColumn>,attendance:JSONObject,timetable:String,apiKey:String=""){try{val json=makeBackup(students,columns,attendance,timetable,"");val e=prefs.edit().putString("auto_backup_latest",json).putString("auto_backup_time",java.time.Instant.now().toString());for(i in 6 downTo 1){val old=prefs.getString("auto_backup_"+i,null);if(old!=null)e.putString("auto_backup_"+(i+1),old)};e.putString("auto_backup_1",json).apply()}catch(_:Exception){}}
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
        val task=if(mode=="test")"فقط عبارت «اتصال موفق است» را برگردان." else if(mode=="quiz")"""بر اساس تنظیمات آزمون که در درخواست آمده، آزمون بساز. دقیقاً نوع سؤال‌های انتخاب‌شده و تعداد تعیین‌شده برای هر نوع را رعایت کن؛ هیچ سؤال اضافی یا نوع انتخاب‌نشده تولید نکن. درجه دشواری انتخاب‌شده را در همه سؤال‌ها رعایت کن. اگر پاسخنامه درخواست شده، آن را در بخش جداگانه با پاسخ صحیح، پاسخ تشریحی کوتاه و بارم هر سؤال ارائه کن؛ در غیر این صورت پاسخ‌ها را نمایش نده. خروجی مختصر، مرتب و بدون مقدمه طولانی باشد. مطالب تأییدنشده را به کتاب نسبت نده.""" else """فقط یک طرح درس کوتاه و اجرایی بنویس؛ حداکثر ۱۸۰ تا ۲۲۰ کلمه. فقط این بخش‌ها را با تیترهای کوتاه بیاور: هدف درس (یک جمله)، شروع و ارزشیابی آغازین (۲ دقیقه)، تدریس و فعالیت دانش‌آموز (با زمان‌بندی متناسب با یک زنگ)، ارزشیابی پایانی (یک روش مشخص)، تکلیف (یک مورد). از مقدمه، توضیح درباره روش جستجو، توصیه‌های عمومی، تکرار هدف‌ها، فهرست منابع طولانی و بخش‌های اضافی خودداری کن. مثال یا فعالیت باید دقیقاً با همان مبحث و پایه متناسب باشد؛ مثال یا صفحه ساختگی نساز."""
        val prompt=if(mode=="test") "فقط عبارت «اتصال موفق است» را برگردان." else """درخواست خام کاربر: $request
ابتدا پایه تحصیلی، نام درس، فصل/درس و عنوان مبحث را استخراج کن. جستجو را با ترکیب دقیق همین مشخصات و عنوان مبحث انجام بده؛ ابتدا منبع رسمی کتاب‌های درسی آموزش‌وپرورش (chap.sch.ir) و سپس شبکه رشد را بررسی کن. نتیجه باید هم‌زمان با پایه و عنوان مبحث تطبیق داشته باشد؛ نتیجه مشابه از پایه یا درس دیگر را استفاده نکن. از جستجوی کلی موضوع بدون پایه و عنوان دقیق پرهیز کن.
اگر پایه یا نام درس یا مبحث مشخص نیست و از متن قابل تشخیص نیست، فقط یک سؤال کوتاه برای تکمیل همان مورد بپرس و طرح درس نساز. اگر منبع معتبر و منطبق پیدا نشد، فقط یک جمله کوتاه اعلام کن که متن دقیق کتاب تأیید نشد و سپس طرح درس عمومیِ محدود به اطلاعات قطعی درخواست ارائه بده؛ هیچ صفحه، تمرین، مثال یا عنوانی را جعل نکن.
$task
فقط خروجی خواسته‌شده را به فارسی بده. از مقدمه، جمع‌بندی، توضیح درباره هوش مصنوعی یا جستجو، منابع مفصل و توصیه‌های اضافه خودداری کن. اگر منبع دقیق پیدا شد، حداکثر یک ارجاع کوتاه در انتهای خروجی بیاور."""
        val body=JSONObject().put("model","openai/gpt-oss-120b")
            .put("messages",JSONArray().put(JSONObject().put("role","system").put("content","دستیار پژوهش آموزشی و طراحی درس؛ جستجوی وب را انجام بده و از جعل منابع پرهیز کن.")).put(JSONObject().put("role","user").put("content",prompt)))
            .put("temperature",0.2).put("max_completion_tokens",2200)
        conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
        val code=conn.responseCode;val raw=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()} ?: ""
        if(code !in 200..299){val err=try{JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()}catch(_:Exception){""};when(code){401->"کلید Groq معتبر نیست.";403->"دسترسی به جستجوی وب یا مدل مجاز نیست.";429->"محدودیت درخواست Groq؛ کمی بعد تلاش کنید.";else->"خطای Groq ($code): ${err.ifBlank{"درخواست ناموفق"}}"}}
        else JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty().ifBlank{"پاسخ جستجو خالی بود؛ درخواست را دقیق‌تر کنید."}
    }catch(e:Exception){"جستجو ناموفق بود: ${e.message ?: "خطای نامشخص"}"}
}
suspend fun testGroqConnection(apiKey:String):String {
    val result=generateLessonWithGroq(apiKey,"آزمایش اتصال","test")
    return if(result.contains("اتصال موفق است"))"اتصال Groq موفق است." else result
}
fun createSecurePreferences(context:android.content.Context):android.content.SharedPreferences {
    fun open():android.content.SharedPreferences {
        val masterKey=MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(context,"yar_moallem_secure",masterKey,EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    return try {
        open().also { it.all }
    } catch (_:Exception) {
        context.deleteSharedPreferences("yar_moallem_secure")
        open()
    }
}
fun lessonTemplate(req:String)= """طرح درس کوتاه
موضوع: $req

• هدف: درک مفهوم اصلی و بیان یک نمونه مرتبط
• شروع کلاس (۳ دقیقه): یک پرسش کوتاه از دانسته‌های قبلی
• تدریس و فعالیت (۲۵ دقیقه): توضیح نکته اصلی، ارائه یک مثال مرتبط و بررسی آن با مشارکت دانش‌آموزان
• ارزشیابی (۵ دقیقه): یک پرسش کاربردی برای سنجش یادگیری
• تکلیف: پاسخ به یک تمرین مرتبط از کتاب درسی
""".trimIndent()
fun loadStudents(raw:String):List<Student> = try { val a=JSONArray(raw);(0 until a.length()).map{val o=a.getJSONObject(it);val name=o.getString("name");val clazz=o.getString("clazz");val sc=o.getJSONObject("scores");val m=mutableMapOf<String,Float>();sc.keys().forEach{m[it]=sc.getDouble(it).toFloat()};val legacyNumber="قدیمی-"+Integer.toUnsignedString((clazz+"|"+name).hashCode());Student(name,clazz,o.getString("subject"),m,o.optString("studentNumber").ifBlank{legacyNumber})} }catch(_:Exception){emptyList()}
fun saveStudents(list:List<Student>):String {val a=JSONArray();list.forEach{s->val scores=JSONObject();s.scores.forEach{(k,v)->scores.put(k,v)};a.put(JSONObject().put("name",s.name).put("studentNumber",s.studentNumber).put("clazz",s.clazz).put("subject",s.subject).put("scores",scores))};return a.toString()}
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