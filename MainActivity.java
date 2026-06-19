package com.my.newproject7;

import android.animation.*;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.appbar.AppBarLayout;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
public class MainActivity extends AppCompatActivity {
	private static final int PERMISSION_REQUEST_CODE = 100;
	private MainBinding binding;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		binding = MainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		initialize(_savedInstanceState);
		initializeLogic();
        
        
        
        
        
        
         proceedToNextActivity();

       // requestStoragePermission();
	}
	
	private void initialize(Bundle _savedInstanceState) {
		setSupportActionBar(binding.Toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		binding.Toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _v) {
				onBackPressed();
			}
		});
	}
	
	private void initializeLogic() {
	}
	


    
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Check read and write permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                
                // Request permissions
                ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    PERMISSION_REQUEST_CODE);
            } else {
                // Permissions already granted
                proceedToNextActivity();
            }
        } else {
            // Android 9 and below, permissions are granted automatically during installation
            proceedToNextActivity();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted
                proceedToNextActivity();
            } else {
                // Permissions denied
                Toast.makeText(this, "File access permission is required to run the app", Toast.LENGTH_LONG).show();
                // Close the app or request permissions again
                finish();
            }
        }
    }
    
    private void proceedToNextActivity() {
        startActivity(new Intent(this, ObfuscationActivity.class));
        finish();
    }
}

