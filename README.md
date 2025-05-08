# Jeopardy! QA Engine IR Project 

A two-stage system:

Lucene‑based retrieval over a 280 K‑page Wikipedia subset

GPT‑4o‑mini reranking of top‑K candidates


# How to run 

1) For the Baseline

Install Java

Install Maven

Go to Jeopardy Directory


```bash
   cd Jeopardy
   mvn clean compile exec:java
```
2) For the LLM Reranker
   
Install Python

```bash
   python3 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
   export OPENAI_API_KEY="your_api_key_here" (Mac/Linux)
   set OPENAI_API_KEY="your_api_key_here" (For windows)
   python jeopardy_llm_reranker.py
```

Example Outputs:

```bash
(venv) amimulehsanzoha@MacBook-Air Jeopardy % ls
dependency-reduced-pom.xml      pom.xml                         reranked_results.txt            target
jeopardy_llm_reranker.py        requirements.txt                src
(venv) amimulehsanzoha@MacBook-Air Jeopardy % mvn clean compile exec:java    
[INFO] Scanning for projects...
[INFO] 
[INFO] ------------------------< org.example:Jeopardy >------------------------
[INFO] Building Jeopardy 1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- clean:3.2.0:clean (default-clean) @ Jeopardy ---
[INFO] Deleting /Users/amimulehsanzoha/Desktop/IR_Amimul_Taoseef/Jeopardy/target
[INFO] 
[INFO] --- resources:3.3.1:resources (default-resources) @ Jeopardy ---
[INFO] Copying 85 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- compiler:3.11.0:compile (default-compile) @ Jeopardy ---
[INFO] Changes detected - recompiling the module! :source
[INFO] Compiling 1 source file with javac [debug release 11] to target/classes
[INFO] 
[INFO] --- exec:3.5.0:java (default-cli) @ Jeopardy ---
******** Welcome to Jeopardy QA Engine! ********
… Indexing from src/main/resources/wiki-subset-20140602/
Total docs: 280715

MRR = 0.3638
P@1 = 0.3000
Found = 49 / 100
Results written to src/main/resources/queriesProcessedBaseline.txt
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:50 min
[INFO] Finished at: 2025-05-07T20:47:01-07:00
[INFO] ------------------------------------------------------------------------
```

```bash
python jeopardy_llm_reranker.py
Reading queries file...
Loaded 100 questions

Baseline Metrics:
MRR = 0.3685
P@1 = 0.3000
Found = 54 / 100

Reranking predictions using GPT-4o-mini...

Reranked Metrics:
MRR = 0.5320
P@1 = 0.5300
Found = 54 / 100

Reranked results written to src/main/resources/queriesReranked.txt
```












