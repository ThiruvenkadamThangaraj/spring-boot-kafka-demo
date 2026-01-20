# ========================================
# Quick Fix: Use Markdown PDF Extension in VS Code
# ========================================

Write-Host "=== Quick PDF Generation with VS Code ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "METHOD 1: Using Markdown PDF Extension (Easiest)" -ForegroundColor Yellow
Write-Host "=============================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Install 'Markdown PDF' extension in VS Code" -ForegroundColor White
Write-Host "   Extension ID: yzane.markdown-pdf" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Open your .md file in VS Code" -ForegroundColor White
Write-Host ""
Write-Host "3. Press Ctrl+Shift+P" -ForegroundColor White
Write-Host ""
Write-Host "4. Type: 'Markdown PDF: Export (pdf)'" -ForegroundColor White
Write-Host ""
Write-Host "5. The PDF will have WORKING links in the TOC!" -ForegroundColor Green
Write-Host ""
Write-Host ""

Write-Host "METHOD 2: Using Pandoc (Best Quality)" -ForegroundColor Yellow
Write-Host "======================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "Run this command:" -ForegroundColor White
Write-Host ""
Write-Host ".\convert-to-pdf-with-working-links.ps1" -ForegroundColor Cyan
Write-Host ""
Write-Host ""

Write-Host "METHOD 3: Online Converter (No Installation)" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Go to: https://www.markdowntopdf.com/" -ForegroundColor White
Write-Host "2. Upload your .md file" -ForegroundColor White
Write-Host "3. Download PDF with working links" -ForegroundColor White
Write-Host ""
Write-Host ""

Write-Host "Which method do you want to try?" -ForegroundColor Cyan
Write-Host ""
Write-Host "[1] Install VS Code extension (Easiest)" -ForegroundColor White
Write-Host "[2] Use Pandoc script (Best quality)" -ForegroundColor White
Write-Host "[3] Just tell me how to fix it" -ForegroundColor White
Write-Host ""

$choice = Read-Host "Enter your choice (1/2/3)"

switch ($choice) {
    "1" {
        Write-Host ""
        Write-Host "Installing Markdown PDF extension..." -ForegroundColor Yellow
        code --install-extension yzane.markdown-pdf
        Write-Host ""
        Write-Host "✅ Extension installed!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Now:" -ForegroundColor Cyan
        Write-Host "1. Open INTERVIEW-QUESTIONS.md in VS Code" -ForegroundColor White
        Write-Host "2. Right-click in editor → 'Markdown PDF: Export (pdf)'" -ForegroundColor White
        Write-Host "3. Your PDF will have working TOC links!" -ForegroundColor Green
    }
    "2" {
        Write-Host ""
        Write-Host "Running Pandoc conversion script..." -ForegroundColor Yellow
        & "$PSScriptRoot\convert-to-pdf-with-working-links.ps1"
    }
    "3" {
        Write-Host ""
        Write-Host "=== How to Fix PDF Index Links ===" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "The problem: Standard PDF converters don't preserve Markdown anchor links" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "The fix: Use Pandoc with these flags:" -ForegroundColor Green
        Write-Host ""
        Write-Host 'pandoc INTERVIEW-QUESTIONS.md -o output.pdf \' -ForegroundColor Cyan
        Write-Host '  --pdf-engine=xelatex \' -ForegroundColor Cyan
        Write-Host '  --toc \' -ForegroundColor Cyan
        Write-Host '  --toc-depth=3 \' -ForegroundColor Cyan
        Write-Host '  --variable colorlinks=true \' -ForegroundColor Cyan
        Write-Host '  --variable linkcolor=blue \' -ForegroundColor Cyan
        Write-Host '  --variable urlcolor=blue \' -ForegroundColor Cyan
        Write-Host '  --variable toccolor=blue' -ForegroundColor Cyan
        Write-Host ""
        Write-Host "This will create a PDF with:" -ForegroundColor White
        Write-Host "  ✅ Clickable TOC entries" -ForegroundColor Green
        Write-Host "  ✅ Working internal section links" -ForegroundColor Green
        Write-Host "  ✅ Working external URL links" -ForegroundColor Green
        Write-Host "  ✅ Proper formatting and syntax highlighting" -ForegroundColor Green
    }
    default {
        Write-Host ""
        Write-Host "Invalid choice. Please run the script again." -ForegroundColor Red
    }
}

Write-Host ""
