package com.example.league95.newsreader;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {
    //List View
    ListView listView;
    //We need an array list for our news titles
    ArrayList<String> titles = new ArrayList<>();
    //And content
    ArrayList<String> content = new ArrayList<>();
    ArrayAdapter<String> arrayAdapter;
    //Let's initialize our data base
    SQLiteDatabase articlesDB;

    /**
     * A method that inserts the values from our current database
     * into our list view. Happens when we first launch the app or download
     * content
     */
    public void updateListView() {
        //Get the content from the database and display the title to the user.
        Cursor c = articlesDB.rawQuery("SELECT * FROM articles", null);
        //indices
        int contentIndex = c.getColumnIndex("content");
        int titleIndex = c.getColumnIndex("title");
        //Check if the result returns anything.
        if (c.moveToFirst()) {
            titles.clear();
            content.clear();

            do {
                titles.add(c.getString(titleIndex));
                content.add(c.getString(contentIndex));

            } while (c.moveToNext());

            arrayAdapter.notifyDataSetChanged();
        }

    }

    /**
     * Create an AsyncTask to handle all the content downloaded.
     */
    public class DownloaderApp extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {

            URL url;
            HttpURLConnection httpURLConnection;
            String result = "";
            String articleTitle = "";
            String articleUrl = "";

            try {
                url = new URL(urls[0]);
                httpURLConnection = (HttpURLConnection) url.openConnection();

                InputStream inputStream = httpURLConnection.getInputStream();
                InputStreamReader reader = new InputStreamReader(inputStream);

                int data;
                char c;

                data = reader.read();
                while (data != -1) {
                    c = (char) data;
                    result += c;
                    data = reader.read();
                }


                //We then need to JSON our elements
                JSONArray jsonArray = new JSONArray(result);
                //We only need 20 items from our JSON array
                int maxItems = 20;
                //If however the max items is greater than our json array
                if (jsonArray.length() < maxItems) {
                    maxItems = jsonArray.length();
                }
                //Before we read the content and insert into database
                //Clear the database just in case to avoid duplication!!
                articlesDB.execSQL("DELETE FROM articles");
                for (int i = 0; i < maxItems; i++) {

                    //Remember each json item is an article id!!
                    String articleId = jsonArray.getString(i);

                    url = new URL("https://hacker-news.firebaseio.com/v0/item/" + articleId + ".json?print=pretty");

                    httpURLConnection = (HttpURLConnection) url.openConnection();

                    inputStream = httpURLConnection.getInputStream();
                    reader = new InputStreamReader(inputStream);

                    data = reader.read();

                    String articleInfo = "";

                    while (data != -1) {
                        c = (char) data;

                        articleInfo += c;
                        data = reader.read();
                    }

                    //Json array is used only when we have one type of info!!
                    //In this case we use JSONObject since our articleInfo
                    //Contains multiple types of content.
                    JSONObject jsonObject = new JSONObject(articleInfo);
                    if (!jsonObject.isNull("title") && !jsonObject.isNull("url")) {
                        //Get the article title
                        articleTitle = jsonObject.getString("title");
                        //Article url
                        articleUrl = jsonObject.getString("url");
                        Log.i("Info ", articleTitle + " "+articleUrl);
                        url = new URL(articleUrl);
                        httpURLConnection = (HttpURLConnection) url.openConnection();
                        inputStream = httpURLConnection.getInputStream();
                        reader = new InputStreamReader(inputStream);
                        Scanner sc = new Scanner(inputStream);
                        //data = reader.read();
                        String articleContent = "";
                        while (sc.hasNextLine()) {
                            //c = (char) data;
                            articleContent += sc.nextLine();
                            //data = reader.read();
                        }
                        //Log.i("Content:", articleContent);
                        //First prepare the query
                        String sql = "INSERT INTO articles (articleId, title, content) VALUES (?, ?, ?)";
                        //Use the sql string now to prepare a statement
                        SQLiteStatement statement = articlesDB.compileStatement(sql);
                        //We bind a string into our statement to fill the ? marks
                        statement.bindString(1, articleId);
                        statement.bindString(2, articleTitle);
                        statement.bindString(3, articleContent);
                        //Execute
                        statement.execute();

                    }


                }
            } catch (MalformedURLException e) {
                System.out.println("Url is invalid!");

            } catch (IOException e) {
                System.out.println("IO exception occurred.");

            } catch (JSONException e) {
                System.out.println("Could not parse to json.");

            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            updateListView();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Create a list view first!
        listView = findViewById(R.id.listView);
        //Adapter
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                Intent intent = new Intent(getApplicationContext(), ArticleActivity.class);
                intent.putExtra("content", content.get(i));

                startActivity(intent);

            }
        });

        //Setup the database
        articlesDB = this.openOrCreateDatabase("Articles", MODE_PRIVATE, null);
        //Create a table for our data
        articlesDB.execSQL("CREATE TABLE IF NOT EXISTS articles (id INTEGER PRIMARY KEY," +
        "articleId INTEGER, title VARCHAR, content VARCHAR)");
        //Also update list view whenever we start the app.
        updateListView();

        //Run our task manager
        //String URL
        String url = "https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty";
        DownloaderApp downloaderApp = new DownloaderApp();
        //Surround with try catch just in case!
        try {
            downloaderApp.execute(url);
        } catch (Exception e) {
            System.out.println("Could not download this website. Exiting...");
        }
    }

}
