'''
CSC 583 Final Project Jeopardy 
Authors: Amimul Ehsan Zoha, Taoseef Aziz
Please set up an open api key in your env and pip install the requirements to run
this script. We have the an example output file uploaded for k =20 (queriesReranked.txt)
for your convenience.
Thanks for the awesome semester :)
'''

import os
import re
import time
import json
from tqdm import tqdm
from dotenv import load_dotenv
from openai import OpenAI
import re

load_dotenv()
client = OpenAI(api_key=os.getenv('OPENAI_API_KEY'))

def parse_preds(preds_raw):
    preds_raw = preds_raw.replace('\n', ' ').strip()
    match = re.search(r'\[(.*)\]', preds_raw)
    if not match:
        return []
    list_content = match.group(1)
    items = [item.strip().strip('"').strip("'") for item in list_content.split(',')]
    items = [item for item in items if item]
    return items

def read_queries_processed(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    #split on lines "|" 
    blocks = re.split(r'(?m)^\|\s*$', content)
    results = []
    for block in blocks:
        if '[content]' in block:
            parts = block.split('[content]')
            if len(parts) >= 4:
                question    = parts[1].strip()
                gold_answer = parts[2].strip()
                preds_raw   = parts[3].strip()
                preds       = parse_preds(preds_raw)
                results.append({
                    'question': question,
                    'gold_answer': gold_answer,
                    'preds': preds
                })
    return results




def rerank_with_gpt4o_mini(question, preds):
    """Rerank predictions using GPT-4o-mini."""
    if not preds:
        return preds
    
    prompt = f"""
Given the Jeopardy question: "{question}",
Here are the candidate answers (Wikipedia page titles):
"""
    for i, pred in enumerate(preds):
        prompt += f"\n{i+1}. {pred}"
    
    prompt += """
Rerank these answers from most likely to least likely to be the correct answer to the Jeopardy question.
Return ONLY the reordered list of answers, numbered 1 to N.
"""

    try:
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": "You are a helpful assistant that reranks candidate answers based on relevance to the Jeopardy question."},
                {"role": "user", "content": prompt}
            ],
            max_tokens=500,
            temperature=0
        )
        
        content = response.choices[0].message.content
        matches = re.findall(r'(\d+)[\.|\)]\s+(.*?)(?=\n\d+[\.|\)]|\Z)', content, re.DOTALL)
        reranked = []
        
        if matches:
            for _, item in matches:
                clean_item = item.strip()
                if clean_item in preds:
                    reranked.append(clean_item)
                    continue
                for pred in preds:
                    if pred.lower() in clean_item.lower() or clean_item.lower() in pred.lower():
                        if pred not in reranked:
                            reranked.append(pred)
                            break
        remaining = [p for p in preds if p not in reranked]
        reranked.extend(remaining)
        
        return reranked
    except Exception as e:
        print(f"Error in reranking: {e}")
        return preds

def compute_metrics(data, use_reranked=False):
    """Compute MRR and P@1 metrics."""
    total = len(data)
    found = 0
    correct_at_1 = 0
    mrr_sum = 0
    
    for entry in data:
        gold = entry['gold_answer']
        preds = entry['reranked_preds'] if use_reranked and 'reranked_preds' in entry else entry['preds']
        
        if preds and gold in preds:
            found += 1
            rank = preds.index(gold) + 1
            mrr_sum += 1.0 / rank
            if preds[0] == gold:
                correct_at_1 += 1
    
    mrr = mrr_sum / total
    p_at_1 = correct_at_1 / total
    
    return {
        'MRR': mrr,
        'P@1': p_at_1,
        'Found': found,
        'Total': total
    }

def main():
    file_path = 'src/main/resources/queriesProcessed.txt'
    output_file = 'src/main/resources/queriesReranked.txt'  
    print("Reading queries file...")
    data = read_queries_processed(file_path) 
    print(f"Loaded {len(data)} questions")
    baseline_metrics = compute_metrics(data)
    print("\nBaseline Metrics:")
    print(f"MRR = {baseline_metrics['MRR']:.4f}")
    print(f"P@1 = {baseline_metrics['P@1']:.4f}")
    print(f"Found = {baseline_metrics['Found']} / {baseline_metrics['Total']}")
    
    #Reranking
    print("\nReranking predictions using GPT-4o-mini...")
    for entry in tqdm(data):
        question = entry['question']
        preds = entry['preds']
        if preds:
            reranked = rerank_with_gpt4o_mini(question, preds)
            entry['reranked_preds'] = reranked
            time.sleep(0.2)

    reranked_metrics = compute_metrics(data, use_reranked=True)
    print("\nReranked Metrics:")
    print(f"MRR = {reranked_metrics['MRR']:.4f}")
    print(f"P@1 = {reranked_metrics['P@1']:.4f}")
    print(f"Found = {reranked_metrics['Found']} / {reranked_metrics['Total']}")
 
    with open(output_file, 'w', encoding='utf-8') as f:
        out = []
        for entry in data:
            question = entry['question']
            gold = entry['gold_answer']
            preds = entry['preds']
            reranked = entry['reranked_preds'] if 'reranked_preds' in entry else preds  
            out.append("|\n[content]\n")
            out.append(question + "\n[content]\n")
            out.append(gold + "\n[content]\n")
            out.append(str(preds) + "\n[content]\n")
            out.append(str(reranked) + "\n|\n")
        
        f.write(''.join(out))
    
    print(f"\nReranked results written to {output_file}")

if __name__ == '__main__':
    main()
