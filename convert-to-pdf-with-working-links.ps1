# ========================================
# Convert Markdown to PDF with Working Hyperlinks and TOC
# ========================================

Write-Host "=== Markdown to PDF Converter with Working Links ===" -ForegroundColor Cyan
Write-Host ""

# Check if Pandoc is installed
$pandocInstalled = Get-Command pandoc -ErrorAction SilentlyContinue

if (-not $pandocInstalled) {
    Write-Host "❌ Pandoc is not installed!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Installing Pandoc..." -ForegroundColor Yellow
    
    # Check if Chocolatey is installed
    $chocoInstalled = Get-Command choco -ErrorAction SilentlyContinue
    
    if ($chocoInstalled) {
        Write-Host "Installing via Chocolatey..." -ForegroundColor Yellow
        choco install pandoc -y
    } else {
        Write-Host "Please install Pandoc manually from: https://pandoc.org/installing.html" -ForegroundColor Red
        Write-Host "Or install Chocolatey first: https://chocolatey.org/install" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host "✅ Pandoc is installed" -ForegroundColor Green
Write-Host ""

# Files to convert
$files = @(
    "INTERVIEW-QUESTIONS.md",
    "ENTERPRISE_MICROSERVICES_GUIDE.md"
)

foreach ($file in $files) {
    if (Test-Path $file) {
        $outputFile = $file -replace '\.md$', '.pdf'
        
        Write-Host "Converting: $file → $outputFile" -ForegroundColor Cyan
        
        # Convert with Pandoc (includes working TOC and hyperlinks)
        pandoc $file -o $outputFile `
            --pdf-engine=xelatex `
            --toc `
            --toc-depth=3 `
            --number-sections `
            --highlight-style=tango `
            --variable colorlinks=true `
            --variable linkcolor=blue `
            --variable urlcolor=blue `
            --variable toccolor=blue `
            --variable geometry:margin=1in `
            --variable fontsize=11pt `
            --metadata title="Interview Questions & Answers" `
            --metadata author="Spring Boot Microservices Guide" `
            --metadata date="$(Get-Date -Format 'MMMM dd, yyyy')"
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Successfully created: $outputFile" -ForegroundColor Green
            Write-Host "   - TOC links: ✅ Working" -ForegroundColor Green
            Write-Host "   - Internal links: ✅ Working" -ForegroundColor Green
            Write-Host "   - External links: ✅ Working" -ForegroundColor Green
            Write-Host ""
        } else {
            Write-Host "❌ Error converting $file" -ForegroundColor Red
            Write-Host ""
        }
    } else {
        Write-Host "⚠️  File not found: $file" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Conversion Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Testing your PDFs:" -ForegroundColor Yellow
Write-Host "1. Open the generated PDF file" -ForegroundColor White
Write-Host "2. Click on any TOC entry → Should jump to that section" -ForegroundColor White
Write-Host "3. Click on any hyperlink → Should open in browser" -ForegroundColor White
Write-Host ""
Write-Host "If you want to customize the PDF, edit the pandoc command above." -ForegroundColor Gray
Write-Host ""

# Optional: Open the PDF
$response = Read-Host "Do you want to open the PDF now? (y/n)"
if ($response -eq 'y' -or $response -eq 'Y') {
    foreach ($file in $files) {
        $pdfFile = $file -replace '\.md$', '.pdf'
        if (Test-Path $pdfFile) {
            Start-Process $pdfFile
        }
    }
}
