package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DemoApplication {

    @GetMapping("/")
    public String hello() {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>DevOps Demo - OKE & ArgoCD</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        background: linear-gradient(to right, #cc4e14ff, #2a5298);
                        color: #fff;
                        text-align: center;
                        padding: 50px;
                    }
                    h1 {
                        font-size: 2.5em;
                        margin-bottom: 20px;
                    }
                    h2 {
                        margin-top: 10px;
                        font-weight: normal;
                    }
                    .badge {
                        display: inline-block;
                        background: #ff9800;
                        padding: 10px 20px;
                        border-radius: 20px;
                        font-weight: bold;
                        color: #000;
                        margin: 10px;
                    }
                    footer {
                        margin-top: 50px;
                        font-size: 0.9em;
                        opacity: 0.7;
                    }
                </style>
            </head>
            <body>
                <h1> Aula de CI/CD com OKE na OCI </h1>
                <h2>Rodando no <span class="badge">OKE</span> com <span class="badge">ArgoCD</span></h2>
                <h2> Entrega Contínua na <b>Oracle Cloud Infrastructure</b></h2>
                <footer>&copy; 2025 | DevOps - Java + Kubernetes + ArgoCD</footer>
            </body>
            </html>
        """;
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
