package com.troon.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    // Register the activity result launcher inside the activity class
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            auth = FirebaseAuth.getInstance()

            // Configure Google Sign-In
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("624747205743-ak70beh3mcljkioomll2rkffvncgvn3u.apps.googleusercontent.com")  // Replace with your web client ID
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)

            if (auth.currentUser != null) {
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
                finish()

            } else {
                // User is not logged in
                SignInScreen(googleSignInClient)
            }
        }
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val intent = Intent(this, DashboardActivity::class.java)
                        startActivity(intent)
                        finish()

                    } else {
                        // If sign-in fails
                        showError(task.exception?.message)
                    }
                }
        } catch (e: ApiException) {
            showError(e.message)
        }
    }

    private fun showError(message: String?) {
        message?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun SignInScreen(googleSignInClient: GoogleSignInClient) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background image
            Image(
                painter = painterResource(id = R.drawable.background_image), // Replace with your background image resource
                contentDescription = "Background Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom // Align the button at the bottom
            ) {
                // Spacer to push the button to the bottom
                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val signInIntent = googleSignInClient.signInIntent
                        // Launch the Google sign-in intent
                        googleSignInLauncher.launch(signInIntent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F0F0)),  // Light grey color
                    shape = RoundedCornerShape(50),  // Rounded corners
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(48.dp)
                ) {
                    // Google Logo + Text
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.google_logo), // Replace with your Google logo resource
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Continue with Google",
                            style = TextStyle(
                                fontFamily = FontFamily(Font(R.font.google_sans)), // Use Google Sans font
                                color = Color.Black // Text color set to black
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSignInScreen() {
    SignInScreen(googleSignInClient = GoogleSignIn.getClient(LocalContext.current, GoogleSignInOptions.DEFAULT_SIGN_IN))
}

@Composable
fun SignInScreen(googleSignInClient: GoogleSignInClient) {
    TODO("Not yet implemented")
}
