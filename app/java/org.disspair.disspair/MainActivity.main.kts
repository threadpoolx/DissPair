#!/usr/bin/env kotlin

package org.disspair.disspair

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This sets your dark background (equivalent to Window.clearcolor)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0A0A0F)
            ) {
                DissPairUI()
            }
        }
    }
}

@Composable
fun DissPairUI() {
    // Column stacks items vertically (equivalent to BoxLayout orientation='vertical')
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Title
        Text(
            text = "DissPair",
            color = Color(0xFF00E5FF),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        // App Subtitle
        Text(
            text = "Bluetooth Analysis Toolkit",
            color = Color(0xFF44445A),
            fontSize = 14.sp
        )

        // Adds empty space between the text and the button
        Spacer(modifier = Modifier.height(32.dp))

        // Scan Button
        Button(
            onClick = {
                // This will print to the console when clicked
                println("Scan button clicked!")
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F61B7)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(text = "SCAN CLASSIC", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}