package com.example.myapp.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ChangePasswordScreen(
    onPasswordChanged: () -> Unit,
    onCancel: () -> Unit
) {
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text("Change Password", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(
                value = newPass,
                onValueChange = { newPass = it },
                label = { Text("New password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmPass,
                onValueChange = { confirmPass = it },
                label = { Text("Confirm new password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    if (newPass != confirmPass) {
                        errorMessage = "Passwords do not match"
                        return@Button
                    }

                    if (user == null) {
                        errorMessage = "User not logged in"
                        return@Button
                    }

                    isLoading = true
                    user.updatePassword(newPass).addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful) {
                            onPasswordChanged()
                        } else {
                            errorMessage = task.exception?.message ?: "Failed to change password"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = 20.dp)
            ) {
                if (isLoading)
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else
                    Text("Change Password", fontSize = 18.sp)
            }

            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
