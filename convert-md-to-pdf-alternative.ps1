# ========================================
# Alternative: Convert MD to HTML then PDF (Working Links)
# ========================================

Write-Host "=== Alternative: Markdown → HTML → PDF ===" -ForegroundColor Cyan
Write-Host ""

# Files to convert
$files = @(
    "INTERVIEW-QUESTIONS.md",
    "ENTERPRISE_MICROSERVICES_GUIDE.md"
)

# Check if wkhtmltopdf is installed
$wkInstalled = Get-Command wkhtmltopdf -ErrorAction SilentlyContinue

if (-not $wkInstalled) {
    Write-Host "❌ wkhtmltopdf is not installed!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Installing wkhtmltopdf..." -ForegroundColor Yellow
    
    $chocoInstalled = Get-Command choco -ErrorAction SilentlyContinue
    if ($chocoInstalled) {
        choco install wkhtmltopdf -y
    } else {
        Write-Host "Download from: https://wkhtmltopdf.org/downloads.html" -ForegroundColor Yellow
        exit 1
    }
}

foreach ($file in $files) {
    if (Test-Path $file) {
        $htmlFile = $file -replace '\.md$', '.html'
        $pdfFile = $file -replace '\.md$', '.pdf'
        
        Write-Host "Converting: $file" -ForegroundColor Cyan
        
        # Step 1: Convert MD to HTML using PowerShell
        $content = Get-Content $file -Raw
        
        # Create HTML with styling
        $html = @"
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            margin: 40px;
            color: #333;
        }
        h1, h2, h3, h4 { color: #2c3e50; margin-top: 24px; }
        h1 { font-size: 28px; border-bottom: 3px solid #3498db; padding-bottom: 10px; }
        h2 { font-size: 24px; border-bottom: 2px solid #95a5a6; padding-bottom: 8px; }
        h3 { font-size: 20px; color: #16a085; }
        code {
            background-color: #f4f4f4;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Courier New', monospace;
        }
        pre {
            background-color: #f4f4f4;
            padding: 15px;
            border-radius: 5px;
            overflow-x: auto;
            border-left: 4px solid #3498db;
        }
        a {
            color: #3498db;
            text-decoration: none;
        }
        a:hover {
            text-decoration: underline;
        }
        table {
            border-collapse: collapse;
            width: 100%;
            margin: 20px 0;
        }
        th, td {
            border: 1px solid #ddd;
            padding: 12px;
            text-align: left;
        }
        th {
            background-color: #3498db;
            color: white;
        }
        tr:nth-child(even) {
            background-color: #f9f9f9;
        }
        .toc {
            background-color: #ecf0f1;
            padding: 20px;
            border-radius: 5px;
            margin-bottom: 30px;
        }
        .toc a {
            display: block;
            padding: 5px 0;
        }
    </style>
</head>
<body>
$content
</body>
</html>
"@
        
        $html | Out-File -FilePath $htmlFile -Encoding UTF8
        
        Write-Host "  → Created: $htmlFile" -ForegroundColor Green
        
        # Step 2: Convert HTML to PDF with working links
        wkhtmltopdf `
            --enable-internal-links `
            --enable-external-links `
            --print-media-type `
            --enable-local-file-access `
            --no-stop-slow-scripts `
            --javascript-delay 1000 `
            --footer-center "Page [page] of [topage]" `
            --footer-font-size 9 `
            --margin-top 20mm `
            --margin-bottom 20mm `
            --margin-left 20mm `
            --margin-right 20mm `
            $htmlFile $pdfFile
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Successfully created: $pdfFile" -ForegroundColor Green
            
            # Clean up HTML file
            Remove-Item $htmlFile -Force
            Write-Host ""
        } else {
            Write-Host "❌ Error converting $file" -ForegroundColor Red
        }
    }
}

Write-Host ""
Write-Host "=== Done ===" -ForegroundColor Cyan
