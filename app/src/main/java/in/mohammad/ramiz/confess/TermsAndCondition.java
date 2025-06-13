package in.mohammad.ramiz.confess;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import in.mohammad.ramiz.confess.adapters.TermsListAdapter;
import in.mohammad.ramiz.confess.entityfiles.ListEntites;
import in.mohammad.ramiz.confess.popups.OkPopUp;

public class TermsAndCondition extends AppCompatActivity {

    private ListView termsListView;
    private ArrayList<ListEntites> termsArrayList;
    private static TermsListAdapter listAdapter;
    private CheckBox termsCheckBox;
    private boolean isAccepted = false;
    private FrameLayout googleButton;
    private OkPopUp popUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_terms_and_condition);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ListView termsListView = findViewById(R.id.listView);
        termsCheckBox = findViewById(R.id.termAccept);
        googleButton = findViewById(R.id.GoogleButton);

        termsArrayList= new ArrayList<>();

        termsCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton compoundButton, boolean b) {
                isAccepted = true;
            }
        });

        googleButton.setOnClickListener(v -> {
            if(isAccepted){
                Intent alaisPage = new Intent(getApplicationContext(), AlaisPage.class);
                startActivity(alaisPage);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            }
            else {
                popUp = new OkPopUp(this, R.drawable.terms_animation, "Terms are not accepted");
            }
        });

        try {
            String jsonString = loadJSONFromAsset();
            JSONArray jsonArray = new JSONArray(jsonString);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject group = jsonArray.getJSONObject(i);
                String heading = group.getString("heading");
                JSONArray terms = group.getJSONArray("terms");

                termsArrayList.add(new ListEntites(heading, true));

                for (int j = 0; j < terms.length(); j++) {
                    termsArrayList.add(new ListEntites(terms.getString(j), false));
                }
            }

            listAdapter = new TermsListAdapter(termsArrayList, this);
            termsListView.setAdapter(listAdapter);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private String loadJSONFromAsset() {
        try {
            InputStream is = getAssets().open("terms.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

}