package net.placemarkt;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class App
{
    public static void main( String[] args )
    {
      InputStream inputStream = App.class.getClassLoader().getResourceAsStream("templates.json");
      String result = new BufferedReader(new InputStreamReader(inputStream))
          .lines().collect(Collectors.joining("\n"));
      System.out.println(result);
    }
}
