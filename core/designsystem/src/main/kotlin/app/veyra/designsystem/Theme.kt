package app.veyra.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val dark = darkColorScheme(
    primary=Color(0xFFB7A1FF), onPrimary=Color(0xFF251B46), primaryContainer=Color(0xFF332A50), onPrimaryContainer=Color(0xFFE7DDFF),
    secondary=Color(0xFF88D5BA), onSecondary=Color(0xFF153B2E), secondaryContainer=Color(0xFF203D34), onSecondaryContainer=Color(0xFFAEF1D5),
    tertiary=Color(0xFFF3C58A), background=Color(0xFF111116), onBackground=Color(0xFFF5F2FA),
    surface=Color(0xFF1C1C24), onSurface=Color(0xFFF5F2FA), surfaceVariant=Color(0xFF282832), onSurfaceVariant=Color(0xFFAAA6B9),
    outline=Color(0xFF484550), outlineVariant=Color(0xFF303039), error=Color(0xFFFFA59D),
    surfaceContainer=Color(0xFF1C1C24), surfaceContainerLow=Color(0xFF18181F), surfaceContainerHigh=Color(0xFF25252E)
)
private val light = lightColorScheme(
    primary=Color(0xFF6D4BC3), onPrimary=Color.White, primaryContainer=Color(0xFFEAE2FF), onPrimaryContainer=Color(0xFF392268),
    secondary=Color(0xFF267A5D), secondaryContainer=Color(0xFFDDF4E9), onSecondaryContainer=Color(0xFF144D38),
    tertiary=Color(0xFF996127), background=Color(0xFFF7F6FA), onBackground=Color(0xFF25222D),
    surface=Color.White, onSurface=Color(0xFF25222D), surfaceVariant=Color(0xFFEEEAF3), onSurfaceVariant=Color(0xFF716A80),
    outline=Color(0xFFB7B0C4), outlineVariant=Color(0xFFE5DFEC), surfaceContainer=Color.White, surfaceContainerLow=Color(0xFFF8F7FB), surfaceContainerHigh=Color(0xFFF0ECF5)
)
private val manrope=FontFamily(Font(R.font.manrope_400,FontWeight.Normal),Font(R.font.manrope_500,FontWeight.Medium),Font(R.font.manrope_600,FontWeight.SemiBold),Font(R.font.manrope_700,FontWeight.Bold),Font(R.font.manrope_800,FontWeight.ExtraBold))
private fun type(size:Int,weight:FontWeight=FontWeight.Normal,line:Int=size+8)=TextStyle(fontFamily=manrope,fontSize=size.sp,fontWeight=weight,lineHeight=line.sp)
private val typography=Typography(
    displayLarge=type(54,FontWeight.ExtraBold,60),displayMedium=type(44,FontWeight.ExtraBold,50),displaySmall=type(36,FontWeight.Bold,44),
    headlineLarge=type(30,FontWeight.ExtraBold,38),headlineMedium=type(26,FontWeight.Bold,34),headlineSmall=type(23,FontWeight.Bold,30),
    titleLarge=type(20,FontWeight.Bold,28),titleMedium=type(16,FontWeight.Bold,24),titleSmall=type(14,FontWeight.SemiBold,22),
    bodyLarge=type(16,line=25),bodyMedium=type(14,line=22),bodySmall=type(12,line=19),
    labelLarge=type(13,FontWeight.Bold,20),labelMedium=type(11,FontWeight.Bold,17),labelSmall=type(10,FontWeight.Bold,16)
)
@Composable fun VeyraTheme(mode:String="Escuro",content:@Composable ()->Unit){
    val isDark=mode in listOf("Escuro","AMOLED") || mode=="Sistema" && isSystemInDarkTheme()
    val colors=when{
        mode=="Dinâmico" && Build.VERSION.SDK_INT>=31->if(isSystemInDarkTheme())dynamicDarkColorScheme(LocalContext.current)else dynamicLightColorScheme(LocalContext.current)
        mode=="AMOLED"->dark.copy(background=Color.Black,surface=Color(0xFF121217))
        isDark->dark
        else->light
    }
    MaterialTheme(colorScheme=colors,typography=typography,shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(28.dp)),content=content)
}
