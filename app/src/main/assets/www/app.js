// STATE VARIABLES
let parentPhoneNumber = localStorage.getItem('parent_phone_number') || '';
let isPremium = localStorage.getItem('is_premium') === 'true';

let currentScreen = 'setup-screen';
let enteredDigits = '';
let unlockInput = '';
let callTimer = null;
let callDurationSeconds = 0;
let adTimerInterval = null;
let adSecondsLeft = 5;

// AUDIO GENERATION (Web Audio API)
let audioCtx = null;

// Character avatars for dialer feedback
const emojis = ['🤖', '🐱', '🐶', '🦊', '🦁', '🐯', '🐼', '🐨', '🦖', '🦄', '🐵', '🐸'];

// Sound notes for each key (Musical dialer)
const keyNotes = {
  '1': 261.63, // C4
  '2': 293.66, // D4
  '3': 329.63, // E4
  '4': 349.23, // F4
  '5': 392.00, // G4
  '6': 440.00, // A4
  '7': 493.88, // B4
  '8': 523.25, // C5
  '9': 587.33, // D5
  '*': 659.25, // E5
  '0': 698.46, // F5
  '#': 783.99  // G5
};

// Initialize audio context on first user click
function initAudio() {
  if (!audioCtx) {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  }
  if (audioCtx.state === 'suspended') {
    audioCtx.resume();
  }
}

// Play a synthesized note
function playTone(freq, type = 'sine', duration = 0.25, volume = 0.1) {
  try {
    initAudio();
    const osc = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();
    
    osc.type = type;
    osc.frequency.setValueAtTime(freq, audioCtx.currentTime);
    
    // Smooth volume envelope to prevent clicking sounds
    gainNode.gain.setValueAtTime(0, audioCtx.currentTime);
    gainNode.gain.linearRampToValueAtTime(volume, audioCtx.currentTime + 0.05);
    gainNode.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + duration);
    
    osc.connect(gainNode);
    gainNode.connect(audioCtx.destination);
    
    osc.start();
    osc.stop(audioCtx.currentTime + duration);
  } catch (e) {
    console.error("Audio error:", e);
  }
}

// Special sound effects
function playRingingSound() {
  // Simulates a telephone ring
  playTone(400, 'sine', 0.5, 0.05);
  setTimeout(() => playTone(450, 'sine', 0.5, 0.05), 100);
}

function playHangupSound() {
  playTone(300, 'sawtooth', 0.15, 0.05);
  setTimeout(() => playTone(150, 'sawtooth', 0.2, 0.05), 150);
}

function playCallConnectedSound() {
  const notes = [523.25, 659.25, 783.99, 1046.50]; // Ascending C major arpeggio
  notes.forEach((note, index) => {
    setTimeout(() => playTone(note, 'sine', 0.15, 0.05), index * 100);
  });
}



// Play a simple synthesized song
function playSong() {
  // Twinkle Twinkle Little Star
  const melody = [
    {note: 261.63, dur: 350}, {note: 261.63, dur: 350},
    {note: 392.00, dur: 350}, {note: 392.00, dur: 350},
    {note: 440.00, dur: 350}, {note: 440.00, dur: 350},
    {note: 392.00, dur: 700},
    {note: 349.23, dur: 350}, {note: 349.23, dur: 350},
    {note: 329.63, dur: 350}, {note: 329.63, dur: 350},
    {note: 293.66, dur: 350}, {note: 293.66, dur: 350},
    {note: 261.63, dur: 700}
  ];

  melody.forEach((item, index) => {
    let accumulatedTime = melody.slice(0, index).reduce((acc, curr) => acc + curr.dur, 0);
    setTimeout(() => {
      // Check if we are still in play screen to continue playing the song
      if (currentScreen === 'play-screen') {
        playTone(item.note, 'triangle', item.dur / 1000, 0.06);
        spawnEmojiParticle(window.innerWidth / 2, window.innerHeight / 2, '🎵');
      }
    }, accumulatedTime);
  });
}

// NAVIGATION FUNCTIONS
function showScreen(screenId) {
  document.querySelectorAll('.screen').forEach(screen => {
    screen.classList.remove('active');
  });
  const target = document.getElementById(screenId);
  if (target) {
    target.classList.add('active');
    currentScreen = screenId;
  }
}

// BOOTSTRAP LOGIC
window.addEventListener('DOMContentLoaded', () => {
  // Prevent default double-tap zoom on iOS
  document.addEventListener('touchstart', (event) => {
    if (event.touches.length > 1) {
      event.preventDefault();
    }
  }, { passive: false });

  // Hook up screen routing
  if (parentPhoneNumber) {
    showScreen('play-screen');
    preventHardwareBack();
    enableKioskMode();
  } else {
    showScreen('setup-screen');
  }

  setupEventListeners();
  initRemoveAdsPrice();
});

function enableKioskMode() {
  if (window.AndroidApp && typeof window.AndroidApp.startKioskMode === 'function') {
    window.AndroidApp.startKioskMode();
  }
}

function disableKioskMode() {
  if (window.AndroidApp && typeof window.AndroidApp.stopKioskMode === 'function') {
    window.AndroidApp.stopKioskMode();
  }
}

// PREVENT TODDLER BACK NAVIGATION OUT OF THE APP (Android back button)
function preventHardwareBack() {
  window.history.pushState(null, null, window.location.href);
  window.addEventListener('popstate', handleBackGesture);
}

function handleBackGesture() {
  window.history.pushState(null, null, window.location.href);
  if (currentScreen === 'play-screen' || currentScreen === 'call-screen') {
    // Redirect child immediately to the parent unlock screen
    openUnlockScreen();
  }
}

// EMOJI RENDER EFFECTS
function spawnEmojiParticle(x, y, emojiChar) {
  const particle = document.createElement('div');
  particle.className = 'emoji-particle';
  particle.textContent = emojiChar || emojis[Math.floor(Math.random() * emojis.length)];
  particle.style.left = `${x}px`;
  particle.style.top = `${y}px`;
  
  // Random throw direction
  const angle = Math.random() * Math.PI * 2;
  const distance = 80 + Math.random() * 100;
  const tx = Math.cos(angle) * distance;
  const ty = Math.sin(angle) * distance;
  const rot = -180 + Math.random() * 360;
  
  particle.style.setProperty('--tx', `${tx}px`);
  particle.style.setProperty('--ty', `${ty}px`);
  particle.style.setProperty('--rot', `${rot}deg`);
  
  document.body.appendChild(particle);
  
  // Clean up
  setTimeout(() => {
    particle.remove();
  }, 800);
}

// CONTROLLER EVENT LISTENERS
function setupEventListeners() {
  // SETUP SCREEN
  const saveSetupBtn = document.getElementById('save-setup-btn');
  const parentPhoneInput = document.getElementById('parent-phone-input');
  const parentPhoneConfirmInput = document.getElementById('parent-phone-confirm-input');
  const errorMsg = document.getElementById('setup-error-msg');
  
  saveSetupBtn.addEventListener('click', () => {
    initAudio();
    const val1 = parentPhoneInput.value.trim().replace(/\s+/g, '');
    const val2 = parentPhoneConfirmInput.value.trim().replace(/\s+/g, '');
    
    if (val1.length < 3) {
      errorMsg.textContent = "⚠️ Ingresa un número válido (mínimo 3 dígitos).";
      errorMsg.style.display = 'block';
      return;
    }

    if (val1 !== val2) {
      errorMsg.textContent = "⚠️ Los números no coinciden. Por favor verifícalos.";
      errorMsg.style.display = 'block';
      return;
    }

    errorMsg.style.display = 'none';
    parentPhoneNumber = val1;
    localStorage.setItem('parent_phone_number', parentPhoneNumber);
    playCallConnectedSound();
    showScreen('play-screen');
    preventHardwareBack();
    enableKioskMode();
  });

  // Presionar Enter pasa al segundo campo o envía
  parentPhoneInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      parentPhoneConfirmInput.focus();
    }
  });

  parentPhoneConfirmInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      saveSetupBtn.click();
    }
  });

  // Ocultar mensaje de error al comenzar a escribir
  parentPhoneInput.addEventListener('input', () => {
    errorMsg.style.display = 'none';
  });

  parentPhoneConfirmInput.addEventListener('input', () => {
    errorMsg.style.display = 'none';
  });

  // PLAY SCREEN DIALER
  const keys = document.querySelectorAll('.key-btn');
  const display = document.getElementById('phone-display');
  const avatar = document.getElementById('toy-avatar');
  
  keys.forEach(key => {
    // Solo aplicar a teclas numéricas que tengan data-key
    if (!key.hasAttribute('data-key')) return;

    key.addEventListener('touchstart', (e) => {
      e.preventDefault(); // Prevents touch lag and double taps
      handleKeyPress(key.getAttribute('data-key'), e.touches[0].clientX, e.touches[0].clientY);
    });
    
    // Desktop mouse fallback
    key.addEventListener('mousedown', (e) => {
      if ('ontouchstart' in window) return;
      handleKeyPress(key.getAttribute('data-key'), e.clientX, e.clientY);
    });
  });

  function handleKeyPress(val, clientX, clientY) {
    if (!val) return;
    initAudio();
    
    // Play sound note
    if (keyNotes[val]) {
      playTone(keyNotes[val], 'triangle', 0.25, 0.08);
    }
    
    // Emojis reaction
    const randomEmoji = emojis[Math.floor(Math.random() * emojis.length)];
    avatar.textContent = randomEmoji;
    spawnEmojiParticle(clientX, clientY, randomEmoji);
    
    // Add to display
    if (enteredDigits.length < 15) {
      enteredDigits += val;
      display.textContent = enteredDigits;
    }
  }

  // Clear button
  const clearBtn = document.getElementById('btn-clear');
  function triggerClear() {
    playTone(180, 'sine', 0.15, 0.08);
    if (enteredDigits.length > 0) {
      enteredDigits = enteredDigits.slice(0, -1);
      display.textContent = enteredDigits.length === 0 ? '¡Marca un número!' : enteredDigits;
    }
  }
  
  clearBtn.addEventListener('touchstart', (e) => {
    e.preventDefault();
    initAudio();
    triggerClear();
  });
  clearBtn.addEventListener('click', (e) => {
    if ('ontouchstart' in window) return;
    initAudio();
    triggerClear();
  });

  // Call button
  const callBtn = document.getElementById('btn-call');
  function triggerCall() {
    if (enteredDigits.length === 0) {
      playTone(180, 'sine', 0.3, 0.08);
      display.textContent = '¡Escribe un número primero!';
      setTimeout(() => {
        if (enteredDigits.length === 0) display.textContent = '¡Marca un número!';
      }, 1500);
      return;
    }
    startCall();
  }

  callBtn.addEventListener('touchstart', (e) => {
    e.preventDefault();
    initAudio();
    triggerCall();
  });
  callBtn.addEventListener('click', (e) => {
    if ('ontouchstart' in window) return;
    initAudio();
    triggerCall();
  });

  // Music button
  const musicBtn = document.getElementById('btn-music');
  function triggerMusic() {
    playSong();
  }

  musicBtn.addEventListener('touchstart', (e) => {
    e.preventDefault();
    initAudio();
    triggerMusic();
  });
  musicBtn.addEventListener('click', (e) => {
    if ('ontouchstart' in window) return;
    initAudio();
    triggerMusic();
  });

  // Lock button
  const parentLockBtn = document.getElementById('parent-lock-btn');
  parentLockBtn.addEventListener('click', () => {
    openUnlockScreen();
  });

  // LLAMADA SIMULADA - HANG UP
  const hangupBtn = document.getElementById('hangup-btn');
  hangupBtn.addEventListener('click', () => {
    endCall();
  });

  // UNLOCK SCREEN (PARENT CONTROL PORTAL)
  const closeUnlockBtn = document.getElementById('cancel-unlock-btn');
  closeUnlockBtn.addEventListener('click', () => {
    showScreen('play-screen');
  });

  const parentKeys = document.querySelectorAll('.parent-key');
  const unlockDisplay = document.getElementById('unlock-display');
  
  parentKeys.forEach(key => {
    key.addEventListener('click', () => {
      const num = key.getAttribute('data-num');
      if (num !== null) {
        playTone(600, 'sine', 0.08, 0.05);
        if (unlockInput.length < 15) {
          unlockInput += num;
          updateUnlockDisplay();
        }
      }
    });
  });

  const btnParentClear = document.getElementById('btn-parent-clear');
  btnParentClear.addEventListener('click', () => {
    playTone(150, 'sine', 0.15, 0.05);
    unlockInput = '';
    updateUnlockDisplay();
  });

  const btnParentOk = document.getElementById('btn-parent-ok');
  btnParentOk.addEventListener('click', () => {
    // Check key
    if (unlockInput === parentPhoneNumber) {
      playCallConnectedSound();
      unlockInput = '';
      updateUnlockDisplay();
      
      // Redirigir al Panel de Padres
      showScreen('parents-panel-screen');
      updateAllAnimalRowsUI();
    } else {
      // Wrong code
      playTone(120, 'sawtooth', 0.4, 0.1);
      const card = document.querySelector('#unlock-screen .card');
      card.classList.add('shake');
      unlockInput = '';
      updateUnlockDisplay();
      setTimeout(() => {
        card.classList.remove('shake');
      }, 400);
    }
  });

  function updateUnlockDisplay() {
    unlockDisplay.textContent = unlockInput.replace(/./g, '•') || '••••••';
  }

  // PREMIUM BUY
  const buyPremiumBtn = document.getElementById('buy-premium-btn');
  buyPremiumBtn.addEventListener('click', () => {
    requestRemoveAdsPurchase();
  });

  // EXIT APP BUTTON / VOLVER AL PANEL
  const exitAppBtn = document.getElementById('exit-app-btn');
  exitAppBtn.addEventListener('click', () => {
    showScreen('parents-panel-screen');
  });

  // BOTÓN ELIMINAR ANUNCIOS DESDE PANEL DE PADRES
  const showPremiumPanelBtn = document.getElementById('show-premium-panel-btn');
  if (showPremiumPanelBtn) {
    showPremiumPanelBtn.addEventListener('click', () => {
      showPremiumCard();
    });
  }

  // SUCCESS SCREEN CLOSE
  const closeSuccessBtn = document.getElementById('close-success-btn');
  closeSuccessBtn.addEventListener('click', () => {
    enteredDigits = '';
    display.textContent = '¡Marca un número!';
    showScreen('play-screen');
  });

  // PANEL DE PADRES - ACCIONES DE NAVEGACIÓN
  const goToExitBtn = document.getElementById('go-to-exit-btn');
  goToExitBtn.addEventListener('click', () => {
    triggerExitFlow();
  });

  const backToGameBtn = document.getElementById('back-to-game-btn');
  backToGameBtn.addEventListener('click', () => {
    enteredDigits = '';
    display.textContent = '¡Marca un número!';
    showScreen('play-screen');
  });

  // PANEL DE PADRES - INICIALIZACIÓN DE GRABACIÓN DE ANIMALES
  const animalKeys = ['perrito', 'gatito', 'dinosaurio', 'panda', 'unicornio'];
  
  animalKeys.forEach(animalKey => {
    const recordBtn = document.getElementById(`record-btn-${animalKey}`);
    const playBtn = document.getElementById(`play-btn-${animalKey}`);
    const deleteBtn = document.getElementById(`delete-btn-${animalKey}`);

    recordBtn.addEventListener('click', () => {
      startVoiceRecording(animalKey);
    });

    playBtn.addEventListener('click', () => {
      playRecordedVoice(animalKey);
    });

    deleteBtn.addEventListener('click', () => {
      deleteRecordedVoice(animalKey);
    });
  });

  const stopRecBtn = document.getElementById('stop-recording-btn');
  stopRecBtn.addEventListener('click', () => {
    stopVoiceRecording(false);
  });
}

// CONFIGURACIÓN DE PERSONAJES
const characters = [
  { key: 'perrito', name: 'Perrito 🐶', avatar: '🐶' },
  { key: 'gatito', name: 'Gatito 🐱', avatar: '🐱' },
  { key: 'dinosaurio', name: 'Dinosaurio 🦖', avatar: '🦖' },
  { key: 'panda', name: 'Panda 🐼', avatar: '🐼' },
  { key: 'unicornio', name: 'Unicornio 🦄', avatar: '🦄' }
];

let activeCharacter = null;
let mediaRecorder = null;
let audioChunks = [];
let recordingInterval = null;
let recordingSecondsRemaining = 5;
let activeRecordingAnimal = null;
let currentlyPlayingAudio = null;

// ACTUALIZAR LA INTERFAZ DE FILAS DE ANIMALES
function updateAllAnimalRowsUI() {
  const animalKeys = ['perrito', 'gatito', 'dinosaurio', 'panda', 'unicornio'];
  animalKeys.forEach(key => {
    const hasVoice = localStorage.getItem(`voice_${key}`) !== null;
    const statusEl = document.getElementById(`status-${key}`);
    const playBtn = document.getElementById(`play-btn-${key}`);
    const deleteBtn = document.getElementById(`delete-btn-${key}`);

    if (hasVoice) {
      statusEl.textContent = '🎙️ Grabación guardada';
      statusEl.classList.add('recorded');
      playBtn.classList.remove('disabled');
      playBtn.removeAttribute('disabled');
      deleteBtn.classList.remove('disabled');
      deleteBtn.removeAttribute('disabled');
    } else {
      statusEl.textContent = 'Voz por defecto';
      statusEl.classList.remove('recorded');
      playBtn.classList.add('disabled');
      playBtn.setAttribute('disabled', 'true');
      deleteBtn.classList.add('disabled');
      deleteBtn.setAttribute('disabled', 'true');
    }
  });
}

// DETECCIÓN DE CÓDECS DE AUDIO COMPATIBLES CON TODOS LOS MÓVILES
function getBestSupportedMimeType() {
  const possibleTypes = [
    'audio/mp4',
    'audio/aac',
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/ogg;codecs=opus',
    'audio/wav',
    'audio/3gpp'
  ];
  if (window.MediaRecorder && typeof MediaRecorder.isTypeSupported === 'function') {
    for (const type of possibleTypes) {
      if (MediaRecorder.isTypeSupported(type)) {
        return type;
      }
    }
  }
  return '';
}

// GRABACIÓN DE AUDIO (Microphone + MediaRecorder Universal)
function startVoiceRecording(animalKey) {
  initAudio();
  activeRecordingAnimal = animalKey;
  audioChunks = [];
  recordingSecondsRemaining = 5;

  const overlay = document.getElementById('recording-overlay');
  const overlayText = document.getElementById('recording-overlay-text');
  const overlayTimer = document.getElementById('recording-overlay-timer');
  const progressFill = document.getElementById('recording-progress-fill');

  const namesMap = {
    perrito: 'Perrito 🐶',
    gatito: 'Gatito 🐱',
    dinosaurio: 'Dinosaurio 🦖',
    panda: 'Panda 🐼',
    unicornio: 'Unicornio 🦄'
  };

  overlayText.textContent = `Grabando voz de ${namesMap[animalKey]}...`;
  overlayTimer.textContent = '5s';
  progressFill.style.width = '0%';
  overlay.classList.remove('hidden');

  const selectedMimeType = getBestSupportedMimeType();
  const audioConstraints = {
    audio: {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
      channelCount: 1
    }
  };

  navigator.mediaDevices.getUserMedia(audioConstraints)
    .then(stream => {
      try {
        const recorderOptions = selectedMimeType ? { mimeType: selectedMimeType } : {};
        mediaRecorder = new MediaRecorder(stream, recorderOptions);
      } catch (e) {
        console.warn("Fallback MediaRecorder option error:", e);
        mediaRecorder = new MediaRecorder(stream);
      }

      mediaRecorder.ondataavailable = event => {
        if (event.data && event.data.size > 0) {
          audioChunks.push(event.data);
        }
      };

      mediaRecorder.onstop = () => {
        // Detener todas las pistas de audio para liberar el micrófono de forma nativa
        stream.getTracks().forEach(track => track.stop());
        
        // Si no fue cancelado, guardar la grabación
        if (audioChunks.length > 0 && activeRecordingAnimal) {
          const chosenType = mediaRecorder.mimeType || selectedMimeType || 'audio/mp4';
          const audioBlob = new Blob(audioChunks, { type: chosenType });
          const reader = new FileReader();
          reader.onloadend = () => {
            const base64Data = reader.result;
            try {
              localStorage.setItem(`voice_${activeRecordingAnimal}`, base64Data);
              updateAllAnimalRowsUI();
            } catch (e) {
              console.error("Error al guardar el audio:", e);
            }
          };
          reader.readAsDataURL(audioBlob);
        }
        
        // Ocultar overlay
        overlay.classList.add('hidden');
        clearInterval(recordingInterval);
      };

      mediaRecorder.start(100); // Tiempos de fragmentos cortos de 100ms para estabilidad en Android

      // Temporizador de cuenta regresiva
      let progress = 0;
      recordingInterval = setInterval(() => {
        recordingSecondsRemaining--;
        overlayTimer.textContent = `${recordingSecondsRemaining}s`;

        if (recordingSecondsRemaining <= 0) {
          stopVoiceRecording(false);
        }
      }, 1000);

      // Animación de barra de progreso
      let progressInterval = setInterval(() => {
        if (overlay.classList.contains('hidden') || recordingSecondsRemaining <= 0) {
          clearInterval(progressInterval);
          return;
        }
        progress += 2;
        if (progress <= 100) {
          progressFill.style.width = `${progress}%`;
        }
      }, 100);

    })
    .catch(err => {
      console.error("Microphone access denied:", err);
      overlayText.textContent = "⚠️ Por favor, otorga el permiso de micrófono al sistema.";
      setTimeout(() => {
        overlay.classList.add('hidden');
      }, 2000);
    });
}

function stopVoiceRecording(cancelled = false) {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    if (cancelled) {
      activeRecordingAnimal = null;
      audioChunks = [];
    }
    mediaRecorder.stop();
  }
}

function playRecordedVoice(animalKey) {
  const audioData = localStorage.getItem(`voice_${animalKey}`);
  if (!audioData) return;

  stopAnyPlayingAudio();
  
  try {
    currentlyPlayingAudio = new Audio(audioData);
    
    // Auto-colgar la llamada cuando la grabación de voz finalice
    currentlyPlayingAudio.addEventListener('ended', () => {
      if (currentScreen === 'call-screen') {
        endCall();
      }
    });

    currentlyPlayingAudio.play();
  } catch (e) {
    console.error("Error playing custom audio:", e);
  }
}

function deleteRecordedVoice(animalKey) {
  stopAnyPlayingAudio();
  localStorage.removeItem(`voice_${animalKey}`);
  updateAllAnimalRowsUI();
}

function stopAnyPlayingAudio() {
  if (currentlyPlayingAudio) {
    currentlyPlayingAudio.pause();
    currentlyPlayingAudio = null;
  }
}


// TOY PHONE CALL SIMULATOR
function startCall() {
  const character = characters[Math.floor(Math.random() * characters.length)];
  activeCharacter = character;
  
  const avatarEl = document.getElementById('calling-avatar');
  const statusEl = document.getElementById('calling-status');
  const durationEl = document.getElementById('call-duration');
  
  avatarEl.textContent = character.avatar;
  statusEl.textContent = `Llamando a ${character.name}...`;
  durationEl.textContent = '00:00';
  
  showScreen('call-screen');
  
  // Ringing phase
  let ringCount = 0;
  const ringInterval = setInterval(() => {
    if (currentScreen !== 'call-screen') {
      clearInterval(ringInterval);
      return;
    }
    playRingingSound();
    ringCount++;
    if (ringCount >= 2) { // 2 rings instead of 3 to connect faster
      clearInterval(ringInterval);
      connectCall(character);
    }
  }, 1000);
}

function connectCall(character) {
  if (currentScreen !== 'call-screen') return;
  
  playCallConnectedSound();
  document.getElementById('calling-status').textContent = `${character.name} en línea`;
  document.getElementById('speaker-wave').style.display = 'flex';
  
  // PLAY CUSTOM VOICE
  const customVoice = localStorage.getItem(`voice_${character.key}`);
  if (customVoice) {
    setTimeout(() => {
      if (currentScreen === 'call-screen') {
        playRecordedVoice(character.key);
      }
    }, 500);
  } else {
    // Si no hay voz grabada, reproducir un tono sintético divertido del animal y colgar a los 4 segundos
    setTimeout(() => {
      if (currentScreen === 'call-screen') {
        if (character.key === 'gatito') {
          playTone(880, 'sine', 0.15, 0.05);
          setTimeout(() => playTone(980, 'sine', 0.25, 0.05), 150);
        } else if (character.key === 'perrito') {
          playTone(220, 'triangle', 0.12, 0.1);
          setTimeout(() => playTone(220, 'triangle', 0.12, 0.1), 180);
        } else if (character.key === 'dinosaurio') {
          playTone(110, 'sawtooth', 0.5, 0.1);
        } else if (character.key === 'panda') {
          playTone(660, 'sine', 0.3, 0.05);
        } else {
          playTone(550, 'sine', 0.2, 0.05);
          setTimeout(() => playTone(770, 'sine', 0.3, 0.05), 200);
        }
        
        // Colgar llamada automáticamente tras 3.5 segundos de silencio
        setTimeout(() => {
          if (currentScreen === 'call-screen') {
            endCall();
          }
        }, 3500);
      }
    }, 1000);
  }
  
  callDurationSeconds = 0;
  callTimer = setInterval(() => {
    callDurationSeconds++;
    const min = String(Math.floor(callDurationSeconds / 60)).padStart(2, '0');
    const sec = String(callDurationSeconds % 60).padStart(2, '0');
    document.getElementById('call-duration').textContent = `${min}:${sec}`;
  }, 1000);
}

function endCall() {
  clearInterval(callTimer);
  stopAnyPlayingAudio();
  playHangupSound();
  showScreen('play-screen');
}

// UNLOCK AND EXIT LOGIC
function openUnlockScreen() {
  unlockInput = '';
  document.getElementById('unlock-display').textContent = '••••••';
  showScreen('unlock-screen');
}

function triggerExitFlow() {
  if (isPremium) {
    if (window.AndroidApp && typeof window.AndroidApp.closeApp === 'function') {
      window.AndroidApp.closeApp();
    } else {
      alert("Modo Premium activo. Cerrando aplicación...");
    }
  } else {
    startAdSimulation();
  }
}

// AD SIMULATOR / GOOGLE ADMOB INTEGRATION
function startAdSimulation() {
  if (window.AndroidApp && typeof window.AndroidApp.showGoogleVideoAd === 'function') {
    window.AndroidApp.showGoogleVideoAd();
  } else {
    if (window.AndroidApp && typeof window.AndroidApp.closeApp === 'function') {
      window.AndroidApp.closeApp();
    } else {
      alert("Cerrando aplicación...");
    }
  }
}

function showPremiumCard() {
  showScreen('ad-screen');
  document.getElementById('ad-video-container').style.display = 'none';
  document.getElementById('premium-card').classList.remove('hidden');
}

// REMOVE ADS PURCHASE (Google Play Billing)
function requestRemoveAdsPurchase() {
  if (window.AndroidApp && typeof window.AndroidApp.purchaseRemoveAds === 'function') {
    window.AndroidApp.purchaseRemoveAds();
  } else {
    alert('Las compras solo están disponibles en la aplicación instalada. 📱');
  }
}

function initRemoveAdsPrice() {
  if (window.AndroidApp && typeof window.AndroidApp.getRemoveAdsPrice === 'function') {
    onRemoveAdsPriceLoaded(window.AndroidApp.getRemoveAdsPrice());
  }
}

function onRemoveAdsPriceLoaded(priceText) {
  document.querySelectorAll('.remove-ads-price').forEach((el) => {
    el.textContent = priceText;
  });
}

function onRemoveAdsPurchased() {
  isPremium = true;
  localStorage.setItem('is_premium', 'true');
  playCallConnectedSound();
  showScreen('premium-success-screen');
}

function onRemoveAdsEntitlementRestored() {
  isPremium = true;
  localStorage.setItem('is_premium', 'true');
}

function onRemoveAdsPurchasePending() {
  alert('Tu pago se está procesando. Los anuncios se quitarán automáticamente cuando se confirme. ⏳');
}

function onRemoveAdsPurchaseFailed(reason) {
  console.error('Remove ads purchase failed:', reason);
  alert('No se pudo completar la compra. Intenta de nuevo. 😕');
}
